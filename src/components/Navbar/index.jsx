import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import './index.css';

const Navbar = () => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  return (
    <div className="NavbarContainer">
      <div className="Logo">ENSIMASSO</div>
      <div className="NavMenu">
        <Link to="/" className="NavItem">Accueil</Link>

        {/* Associations Dropdown */}
        <div className="relative">
          <button
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            className="NavItem flex items-center"
          >
            Associations
            <svg
              className="w-2 h-2 ml-1" // Adjusted width and height for smaller arrow
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 10 6"
            >
              <path
                stroke="currentColor"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d="m1 1 4 4 4-4"
              />
            </svg>
          </button>

          {isDropdownOpen && (
            <div className="absolute z-10 mt-2 w-44 rounded-lg shadow bg-gray-400">
                <li>
                  <Link to="/assos/bdlc" className="block px-4 py-2 hover:bg-gray-700">
                    BDLC
                  </Link>
                </li>
                <li>
                  <Link to="/assos/gala" className="block px-4 py-2 hover:bg-gray-700">
                    GALA
                  </Link>
                </li>
                <li>
                  <Link to="/assos/ensimersion" className="block px-4 py-2 hover:bg-gray-700">
                    ENSIMersion
                  </Link>
                </li>
                <li>
                <Link to="/assos/kfet" className="block px-4 py-2 hover:bg-gray-700">
                  Kfet
                </Link>
                </li>
            </div>
          )}
        </div>

        <Link to="/forum" className="NavItem">Forum</Link>
        <Link to="/events" className="NavItem">Events</Link>
        <Link to="/about" className="NavItem">About</Link>
      </div>
      <div className="AccountMenu">
        <Link to="/login" className="LoginItem">Login</Link>
        <Link to="/" className="ProfileItem">
          <img src="/images/profile.svg" alt="profile" />
        </Link>
      </div>
    </div>
  );
};

export { Navbar };
