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

        <div 
          className="relative"
          onMouseEnter={() => setIsDropdownOpen(true)}
          onMouseLeave={() => setIsDropdownOpen(false)}
        >
          <button className="NavItem flex items-center gap-2">
            Associations 
            <svg
              className="w-2 h-2 ml-1"
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
            <div 
              className="absolute z-10 w-75 rounded-lg shadow bg-gray-400"
              onMouseEnter={() => setIsDropdownOpen(true)}
              onMouseLeave={() => setIsDropdownOpen(false)}
              style={{ top: '100%', left: '20%' }} // Align directly below button
            >
              <ul className="py-2 text-base text-white">
                <li>
                  <Link to="/assos/bdlc" className="DropItem">
                    BDLC
                  </Link>
                </li>
                <li>
                  <Link to="/assos/gala" className="DropItem">
                    GALA
                  </Link>
                </li>
                <li>
                  <Link to="/assos/ensimersion" className="DropItem">
                    ENSIMersion
                  </Link>
                </li>
                <li>
                  <Link to="/assos/kfet" className="DropItem">
                    Kfet
                  </Link>
                </li>
              </ul>
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
