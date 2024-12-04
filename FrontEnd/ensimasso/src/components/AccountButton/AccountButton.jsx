import React from 'react';
import './AccountButton.css';
import { useUser } from '../../contexts/UserContext'; // Import the useUser hook

const AccountButton = ({ onLoginClick, onLogout }) => {
  const { user } = useUser(); // Access the user from context

  const handleLogout = () => {
    onLogout(); // Call the logout function passed via props
  };

  return (
    <div className="AccountButton">
      {user ? (
        <button onClick={handleLogout}>
          {user.fullName} (Logout) {/* Display the user's full name and logout option */}
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

