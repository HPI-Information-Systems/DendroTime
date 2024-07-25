// 'use client';
import React from 'react';
import { Card, ProgressBar } from '@tremor/react';

function classNames(...classes) {
  return classes.filter(Boolean).join(' ');
}

export default function StatusCard({name, active, progress}) {
  const color = active ? undefined : (progress === 100 ? "green" : "gray");

  return (
    <Card key={name} className="m-1">
      <dt className={classNames(
        active ? "font-bold text-tremor-content-strong dark:test-dark-tremor-content-strong"
               : "font-medium text-tremor-content dark:text-dark-tremor-content",
        "text-tremor-default"
      )}>
        {name}
      </dt>
      <dd className="mt-2 flex items-baseline space-x-2.5 ">
        <span className={classNames(
            active ? "font-semibold tremor-content-strong text-tremor-content-strong dark:text-dark-tremor-content-strong"
                   : "font-regular tremor-content text-tremor-content dark:text-dark-tremor-content",
            "text-tremor-metric"
          )}>
          {progress}&nbsp;%
        </span>
      </dd>
      <dd>
        <ProgressBar value={progress} color={color} className={active ? "animate-pulse" : ""} />
      </dd>
    </Card>
  );
}