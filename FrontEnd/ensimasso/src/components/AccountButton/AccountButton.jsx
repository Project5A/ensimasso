import React from 'react';
import { useNavigate } from 'react-router-dom'; // For navigation
import './AccountButton.css';
import { useUser } from '../../contexts/UserContext';

const AccountButton = ({ onLoginClick }) => {
  const { user, logout } = useUser(); // Access the user and logout function from context
  const navigate = useNavigate(); // To navigate to the profile page

  const handleProfileClick = () => {
    navigate('/dashboard'); // Redirect to the profile page
  };

  const handleLogout = () => {
    logout(); // Call the context logout function
  };

  return (
    <div className="AccountButton">
      {user ? (
        <img src={user?.photo || 'assets/profile-circle.svg'}
          alt="Profile"
          className="ProfilePicture"
          onClick={handleProfileClick} // Navigate to profile page on click
          style={{ cursor: 'pointer', width: '50px', height: '50px', borderRadius: '50%'}} // Styling for the profile picture
        />
      ) : (
        <button onClick={onLoginClick}>
          Login {/* Display the login button if the user is not logged in */}
        </button>
      )}
    </div>
  );
};

export { AccountButton };
