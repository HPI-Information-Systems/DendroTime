# copied and adapted from aeon
import glob
import os
import re

import numpy as np



def load_classification(
    name,
    extract_path,
    split=None,
):
    local_module = extract_path
    path = local_module
    if not os.path.exists(path):
        raise ValueError(f"Path {path} does not exist")

    # Test for discrete version (first suffix _disc), always use that if it exists
    train = os.path.join(path, f"{name}/{name}_disc*TRAIN.ts")
    test = os.path.join(path, f"{name}/{name}_disc*TEST.ts")
    train_match = glob.glob(train)
    test_match = glob.glob(test)
    if train_match and test_match:
        name = name + "_disc"

    X, y = _load_saved_dataset(
        name=name,
        split=split,
        local_module=local_module,
    )
    return X, y


def _load_saved_dataset(name, split, local_module):
    dir_name = name
    if isinstance(split, str):
        split = split.upper()
    if split in ("TRAIN", "TEST"):
        fname = name + "_" + split + ".ts"
        abspath = os.path.join(local_module, dir_name, fname)
        X, y, meta_data = load_from_ts_file(abspath)
    # if split is None, load both train and test set
    elif split is None:
        fname = name + "_TRAIN.ts"
        abspath = os.path.join(local_module, dir_name, fname)
        X_train, y_train, meta_data = load_from_ts_file(abspath)

        fname = name + "_TEST.ts"
        abspath = os.path.join(local_module, dir_name, fname)
        X_test, y_test, _ = load_from_ts_file(abspath)
        if meta_data["equallength"]:
            X = np.concatenate([X_train, X_test])
        else:
            X = X_train + X_test
        y = np.concatenate([y_train, y_test])
    else:
        raise ValueError("Invalid `split` value =", split)
    return X, y


def load_from_ts_file(full_file_path_and_name):
    # split the file path into the root and the extension
    root, ext = os.path.splitext(full_file_path_and_name)
    # Append .ts if no extension if found
    if not ext:
        full_file_path_and_name = root + ".ts"
    # Open file
    with open(full_file_path_and_name, encoding="utf-8") as file:
        # Read in headers
        meta_data = _load_header_info(file)
        # load into list of numpy
        data, y = _load_data(file, meta_data)

    # if equal load to 3D numpy
    if meta_data["equallength"]:
        data = np.array(data)
        if meta_data["univariate"]:
            data = data.squeeze()
    return data, y, meta_data


def _load_header_info(file):
    meta_data = {
        "problemname": "none",
        "timestamps": False,
        "missing": False,
        "univariate": True,
        "equallength": True,
        "classlabel": True,
        "targetlabel": False,
        "class_values": [],
    }
    boolean_keys = ["timestamps", "missing", "univariate", "equallength", "targetlabel"]
    for line in file:
        line = line.strip().lower()
        line = re.sub(r"\s+", " ", line)
        if line and not line.startswith("#"):
            tokens = line.split(" ")
            token_len = len(tokens)
            key = tokens[0][1:]
            if key == "data":
                if line != "@data":
                    raise OSError("data tag should not have an associated value")
                return meta_data
            if key in meta_data.keys():
                if key in boolean_keys:
                    if token_len != 2:
                        raise OSError(f"{tokens[0]} tag requires a boolean value")
                    if tokens[1] == "true":
                        meta_data[key] = True
                    elif tokens[1] == "false":
                        meta_data[key] = False
                elif key == "problemname":
                    meta_data[key] = tokens[1]
                elif key == "classlabel":
                    if tokens[1] == "true":
                        meta_data["classlabel"] = True
                        if token_len == 2:
                            raise OSError(
                                "if the classlabel tag is true then class values "
                                "must be supplied"
                            )
                    elif tokens[1] == "false":
                        meta_data["classlabel"] = False
                    else:
                        raise OSError("invalid class label value")
                    meta_data["class_values"] = [token.strip() for token in tokens[2:]]
        if meta_data["targetlabel"]:
            meta_data["classlabel"] = False
    return meta_data


def _load_data(file, meta_data, replace_missing_vals_with="NaN"):
    data = []
    n_cases = 0
    n_channels = 0  # Assumed the same for all
    current_channels = 0
    n_timepoints = 0
    y_values = []
    target = False
    if meta_data["classlabel"] or meta_data["targetlabel"]:
        target = True
    for line in file:
        line = line.strip().lower()
        line = line.replace("nan", replace_missing_vals_with)
        line = line.replace("?", replace_missing_vals_with)
        if "timestamps" in meta_data and meta_data["timestamps"]:
            channels = _get_channel_strings(line, target, replace_missing_vals_with)
        else:
            channels = line.split(":")
        n_cases += 1
        current_channels = len(channels)
        if target:
            current_channels -= 1
        if n_cases == 1:  # Find n_channels and length  from first if not unequal
            n_channels = current_channels
            if meta_data["equallength"]:
                n_timepoints = len(channels[0].split(","))
        else:
            if current_channels != n_channels:
                raise OSError(
                    f"Inconsistent number of dimensions in case {n_cases}. "
                    f"Expecting {n_channels} but have read {current_channels}"
                )
            if meta_data["univariate"]:
                if current_channels > 1:
                    raise OSError(
                        f"Seen {current_channels} in case {n_cases}."
                        f"Expecting univariate from meta data"
                    )
        if meta_data["equallength"]:
            current_length = n_timepoints
        else:
            current_length = len(channels[0].split(","))
        np_case = np.zeros(shape=(n_channels, current_length))
        for i in range(0, n_channels):
            single_channel = channels[i].strip()
            data_series = single_channel.split(",")
            data_series = [float(x) for x in data_series]
            if len(data_series) != current_length:
                equal_length = meta_data["equallength"]
                raise OSError(
                    f"channel {i} in case {n_cases} has a different number of "
                    f"observations to the other channels. "
                    f"Saw {current_length} in the first channel but"
                    f" {len(data_series)} in the channel {i}. The meta data "
                    f"specifies equal length == {equal_length}. But even if series "
                    f"length are unequal, all channels for a single case must be the "
                    f"same length"
                )
            np_case[i] = np.array(data_series)
        data.append(np_case)
        if target:
            y_values.append(channels[n_channels])
    if meta_data["equallength"]:
        data = np.array(data)
    return data, np.asarray(y_values)


def _get_channel_strings(line, target=True, missing="NaN"):
    channel_strings = re.sub(r"\s", "", line)
    channel_strings = channel_strings.split("):")
    c = len(channel_strings)
    if target:
        c = c - 1
    for i in range(c):
        channel_strings[i] = channel_strings[i] + ")"
        numbers = re.findall(r"\d+\.\d+|" + missing, channel_strings[i])
        channel_strings[i] = ",".join(numbers)
    return channel_strings
