import React from "react";
import {CheckCircleIcon, ExclamationTriangleIcon, XCircleIcon} from '@heroicons/react/24/outline';
import {ToastContainer, Slide} from 'react-toastify';

const CustomIcon = props => {
  switch (props.type) {
    // case 'info':
    //   return <Info />;
    case 'success':
      return <CheckCircleIcon className="text-green-500 bg-green-100 dark:bg-green-800 dark:text-green-200"/>;
    case 'error':
      return <XCircleIcon className="text-red-500 bg-red-100 dark:bg-red-800 dark:text-red-200"/>;
    case 'warning':
      return <ExclamationTriangleIcon className="text-orange-500 bg-orange-100 dark:bg-orange-700 dark:text-orange-200"/>;
    default:
      return undefined;
  }
};

function Notifications({props}) {
  return (
    <ToastContainer icon={CustomIcon}
                    autoClose={3000} limit={5}
                    hideProgressBar={false}
                    newestOnTop={true}
                    closeOnClick
                    pauseOnFocusLoss
                    draggable
                    pauseOnHover
                    transition={Slide}
                    {...props} />
  );
}

export default Notifications;
