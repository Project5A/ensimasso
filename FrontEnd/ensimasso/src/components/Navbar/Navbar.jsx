import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import './Navbar.css';
import { AccountButton } from '../AccountButton/AccountButton.jsx';
import { Login } from '../Login/Login.jsx';




const Navbar = () => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isAssociationsOpen, setIsAssociationsOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [isLoginPopupOpen, setIsLoginPopupOpen] = useState(false);

  // Check window width for mobile view
  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 968);
    };

    handleResize(); // Check initial width
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  const toggleLoginPopup = () => {
    setIsLoginPopupOpen(!isLoginPopupOpen);
  };

  const handleLoginSuccess = (name) => {
    // Save user info in localStorage
    localStorage.setItem('user', JSON.stringify({ name }));
    setIsLoginPopupOpen(!isLoginPopupOpen);
  };

  const handleLogout = () => {
    // Perform additional logout actions if needed
    localStorage.removeItem('user');
  };

  return (
    <div className="NavbarContainer">
      <Link to="/" className="Logo">ENSIMASSO</Link>

      {/* Mobile Menu Button */}
      {isMobile && (
        <button
          className={`HamburgerMenu ${isMobileMenuOpen ? 'open' : ''}`}
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="h-6 w-6 text-white"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2"
              d="M4 6h16M4 12h16m-7 6h7"
            />
          </svg>
        </button>
      )}

      {/* Desktop Menu */}
      {!isMobile && (
        <div className="NavMenu flex">
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
                style={{ top: '100%', left: '20%' }}
              >
                <ul className="py-2 text-base text-white">
                  <li>
                  <Link to="/assos/bdlc" className="DropItem">BDLC</Link>
                  </li>
                  <li>
                    <Link to="/association" className="DropItem">GALA</Link>
                  </li>
                  <li>
                    <Link to="/association" className="DropItem">ENSIMersion</Link>
                  </li>
                  <li>
                    <Link to="/association" className="DropItem">Kfet</Link>
                  </li>
                </ul>
              </div>
            )}
          </div>

          <Link to="/forum" className="NavItem">Forum</Link>
          <Link to="/events" className="NavItem">Events</Link>
          <Link to="/about" className="NavItem">About</Link>
        </div>
      )}

      {/* Account Menu */}
      <div className="AccountMenu">
        <AccountButton
          onLoginClick={toggleLoginPopup}
          onLogout={handleLogout}
        />
      </div>

      {/* Login Popup */}
      {isLoginPopupOpen && (
        <div className="popup-overlay">
          <div className="popup">
            <button className="close-button" onClick={toggleLoginPopup}>
              🗙
            </button>
            <Login onLoginSuccess={handleLoginSuccess} />
          </div>
        </div>
      )}

      {/* Mobile Dropdown Menu */}
      {isMobileMenuOpen && (
        <div className="MobileDropdownMenu bg-gray-800 absolute top-16 left-0 w-full p-4">
          <ul className="py-2 text-base text-white">
            <li>
              <button
                className="DropItem"
                onClick={() => setIsAssociationsOpen(!isAssociationsOpen)}
              >
                Associations
                <svg
                  className={`w-4 h-4 transform transition-transform ${
                    isAssociationsOpen ? 'rotate-180' : 'rotate-0'
                  }`}
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M19 9l-7 7-7-7"
                  />
                </svg>
              </button>

              {isAssociationsOpen && (
                <ul className="pl-4 mt-2 space-y-2">
                  <li>
                    <Link to="/assos/bdlc" className="DropItem2">BDLC</Link>
                  </li>
                  <li>
                    <Link to="/assos/gala" className="DropItem2">GALA</Link>
                  </li>
                  <li>
                    <Link to="/assos/ensimersion" className="DropItem2">ENSIMersion</Link>
                  </li>
                  <li>
                    <Link to="/assos/kfet" className="DropItem2">Kfet</Link>
                  </li>
                </ul>
              )}
            </li>
            <li>
              <Link to="/forum" className="DropItem">Forum</Link>
            </li>
            <li>
              <Link to="/events" className="DropItem">Events</Link>
            </li>
            <li>
              <Link to="/about" className="DropItem">About</Link>
            </li>
          </ul>
        </div>
      )}
    </div>
  );
};

export { Navbar };
