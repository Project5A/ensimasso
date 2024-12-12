import React from 'react';
import './AccountButton.css';
import { useUser } from '../../contexts/UserContext';

const AccountButton = ({ onLoginClick }) => {
  const { user, logout } = useUser(); // Access the user and logout function from context

  const handleLogout = () => {
    logout(); // Call the context logout function
  };

  return (
    <div className="AccountButton">
      {user ? (
        <button onClick={handleLogout}>
          {user.username} (Logout) {/* Display the username and logout option */}
        </button>
      ) : (
        <button onClick={onLoginClick}>
          Login {/* Display the login button if the user is not logged in */}
        </button>
      )}
    </div>
  );
};

export { AccountButton };
