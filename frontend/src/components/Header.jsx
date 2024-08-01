import React, {useState} from "react";
import Logo from "../primitives/Logo";
import {
  Dialog,
  DialogPanel,
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
  Popover,
  PopoverButton,
  PopoverGroup,
  PopoverPanel,
} from '@headlessui/react';
import {
  ArrowPathIcon,
  Bars3Icon,
  ChartPieIcon,
  ChevronDownIcon,
  CursorArrowRaysIcon,
  FingerPrintIcon,
  PhoneIcon,
  PlayCircleIcon,
  SquaresPlusIcon,
  XMarkIcon,
} from '@heroicons/react/24/outline';
import {cx} from "../util";
import { Link } from "react-router-dom";

const examples = [
  {name: 'Bar Chart', description: 'Interactive bar chart with React and d3.js', href: '/bar-example', icon: ChartPieIcon},
  {name: 'Dendrogram Test', description: 'Dendrogram created using d3.js with demo data', href: '/dendro-test', icon: CursorArrowRaysIcon},
  // {name: 'Security', description: 'Your customers’ data will be safe and secure', href: '#', icon: FingerPrintIcon},
  // {name: 'Integrations', description: 'Connect with third-party tools', href: '#', icon: SquaresPlusIcon},
  // {name: 'Automations', description: 'Build strategic funnels that will convert', href: '#', icon: ArrowPathIcon},
];
const menuEntries = [
  {name: 'Examples', href: '#', icon: SquaresPlusIcon, items: examples},
  {name: 'Clustering', href: '/', icon: PlayCircleIcon, items: []},
  // {name: 'Previous Runs', href: '#', icon: ChartPieIcon, items: examples},
];

function AppHeader() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="bg-white">
      <nav className="mx-auto flex max-w-7xl items-center justify-between p-6 lg:px-8" aria-label="Global">
        <Logo/>
        <PopoverGroup className="hidden lg:flex lg:gap-x-12">
          {menuEntries.map((entry) => (entry.items.length > 0) ? (
            <Popover key={entry.name} className="relative">
              <PopoverButton className="flex items-center gap-x-1 text-sm font-semibold leading-6 text-gray-900">
                <entry.icon className="h-5 w-5 flex-none text-gray-400" aria-hidden="true"/>
                {entry.name}
                <ChevronDownIcon className="h-5 w-5 flex-none text-gray-400" aria-hidden="true"/>
              </PopoverButton>

              <PopoverPanel
                transition
                className="absolute -left-8 top-full z-10 mt-3 w-screen max-w-md overflow-hidden rounded-3xl bg-white shadow-lg ring-1 ring-gray-900/5 transition data-[closed]:translate-y-1 data-[closed]:opacity-0 data-[enter]:duration-200 data-[leave]:duration-150 data-[enter]:ease-out data-[leave]:ease-in"
              >
                <div className="p-4">
                  {entry.items.map((item) => (
                    <div
                      key={item.name}
                      className="group relative flex items-center gap-x-6 rounded-lg p-4 text-sm leading-6 hover:bg-gray-50"
                    >
                      <div
                        className="flex h-11 w-11 flex-none items-center justify-center rounded-lg bg-gray-50 group-hover:bg-white">
                        <item.icon className="h-6 w-6 text-gray-600 group-hover:text-indigo-600" aria-hidden="true"/>
                      </div>
                      <div className="flex-auto">
                        <Link to={item.href} className="block font-semibold text-gray-900">
                          {item.name}
                          <span className="absolute inset-0"/>
                        </Link>
                        <p className="mt-1 text-gray-600">{item.description}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </PopoverPanel>
            </Popover>
          ) : (
            <Link key={entry.name} to={entry.href} className="flex items-center gap-x-1">
              <entry.icon className="h-5 w-5 flex-none text-gray-400" aria-hidden="true"/>
              <span className="text-sm font-semibold leading-6 text-gray-900">
                {entry.name}
              </span>
            </Link>
          ))}
        </PopoverGroup>
        <div className="flex lg:hidden">
          <button
            type="button"
            className="-m-2.5 inline-flex items-center justify-center rounded-md p-2.5 text-gray-700"
            onClick={() => setMobileMenuOpen(true)}
          >
            <span className="sr-only">Open main menu</span>
            <Bars3Icon className="h-6 w-6" aria-hidden="true"/>
          </button>
        </div>
        <div className="hidden lg:flex lg:flex-1 lg:justify-end">
        </div>
      </nav>
      <Dialog className="lg:hidden" open={mobileMenuOpen} onClose={setMobileMenuOpen}>
        <div className="fixed inset-0 z-10"/>
        <DialogPanel
          className="fixed inset-y-0 right-0 z-10 w-full overflow-y-auto bg-white px-6 py-6 sm:max-w-sm sm:ring-1 sm:ring-gray-900/10">
          <div className="flex items-center justify-between">
            <Logo/>
            <button
              type="button"
              className="-m-2.5 rounded-md p-2.5 text-gray-700"
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="sr-only">Close menu</span>
              <XMarkIcon className="h-6 w-6" aria-hidden="true"/>
            </button>
          </div>
          <div className="mt-6 flow-root">
            <div className="-my-6 divide-y divide-gray-500/10">
              <div className="space-y-2 py-6">
                {menuEntries.map((entry) => entry.items.length > 0 ? (
                  <Disclosure key={entry.name} as="div" className="-mx-3">
                    {({isOpen}) => (
                      <>
                        <DisclosureButton className="flex w-full items-center rounded-lg py-2 pl-3 pr-3.5 hover:bg-gray-50">
                          <entry.icon className="h-5 w-5 flex-none text-gray-400" aria-hidden="true"/>
                          <span className="text-base font-semibold leading-7 text-gray-900 ml-1 mr-1">
                            {entry.name}
                          </span>
                          <div className="flex flex-1 justify-center lg:hidden">
                          </div>
                          <ChevronDownIcon
                            className={cx(isOpen ? 'rotate-180' : '', 'h-5 w-5 flex-none')}
                            aria-hidden="true"
                          />
                        </DisclosureButton>
                        <DisclosurePanel className="mt-2 space-y-2">
                        {entry.items.map((item) => (
                            <DisclosureButton
                              key={item.name}
                              as="a"
                              href={item.href}
                              className="block rounded-lg py-2 pl-6 pr-3 text-sm font-semibold leading-7 text-gray-900 hover:bg-gray-50"
                            >
                              {item.name}
                            </DisclosureButton>
                          ))}
                        </DisclosurePanel>
                      </>
                    )}
                  </Disclosure>
                ) : (
                  <Link key={entry.name}
                     to={entry.href}
                     className="-mx-3 block flex rounded-lg px-3 py-2 hover:bg-gray-50 items-center"
                  >
                    <entry.icon className="h-5 w-5 flex-none text-gray-400 mr-1" aria-hidden="true"/>
                    <span className="text-base font-semibold leading-7 text-gray-900 ">
                      {entry.name}
                    </span>
                  </Link>
                ))}
              </div>
            </div>
          </div>
        </DialogPanel>
      </Dialog>
    </header>
  );
}

export default AppHeader;
