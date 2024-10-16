import React from 'react';
import './Footer.css'

const Footer = () => {
  return (
    <div className="footer_section">
    <footer className="rounded-lg shadow dark:bg-gray-900 m-4 ">
      <div className="w-full max-w-screen-xl mx-auto p-4 md:py-8 ">
        <div className="flex items-center justify-between">
          <a
            href="https://flowbite.com/"
            className="flex items-center mb-4 sm:mb-0 space-x-3 rtl:space-x-reverse"
          >
            <img
              src="https://flowbite.com/docs/images/logo.svg"
              className="h-8"
              alt="Flowbite Logo"
            />
            <span className="self-center text-2xl font-semibold whitespace-nowrap dark:text-white">
              ENSIMASSO
            </span>
          </a>
          <ul className="flex items-center space-x-4 text-sm font-medium text-gray-500 dark:text-gray-400">
            <li>
              <a href="#" className="hover:underline">
                A propos
              </a>
            </li>
            <li>
              <a href="#" className="hover:underline">
                Notre Policy
              </a>
            </li>
            <li>
              <a href="#" className="hover:underline">
                Licensing
              </a>
            </li>
            <li>
              <a href="#" className="hover:underline">
                Contactez Nous
              </a>
            </li>
          </ul>
        </div>
        <hr className="my-2 border-gray-200 sm:mx-auto dark:border-gray-700 lg:my-8" />
        <span className="block text-sm text-gray-500 sm:text-center dark:text-gray-400">
          © 2024{' '}
          <a href="https://flowbite.com/" className="hover:underline">
            ENSIMASSO™
          </a>
          . All Rights Reserved.
        </span>
      </div>
    </footer>
    </div>
  );
};

export {Footer};
