import React, { useRef, useState } from 'react';
import { request } from '../axios_helper'; // Adjust the import path as needed
import { useUser } from '../../contexts/UserContext'; // Import user context
import './Login.css';

const Login = ({ onClose }) => {
  const wrapperRef = useRef(null);
  const { setUser } = useUser(); // Access setUser to update user context
  const [loginData, setLoginData] = useState({ email: '', password: '' });
  const [signupData, setSignupData] = useState({ name: '', email: '', password: '' });
  const [message, setMessage] = useState({ type: '', content: '' }); // Message state

  const handleLoginClick = () => {
    if (wrapperRef.current) {
      wrapperRef.current.classList.add('active');
    }
    setMessage({ type: '', content: '' }); // Reset message
  };

  const handleSignupClick = () => {
    if (wrapperRef.current) {
      wrapperRef.current.classList.remove('active');
    }
    setMessage({ type: '', content: '' }); // Reset message
  };

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await request('POST', '/api/auth/login', loginData);
      const { token, user } = response.data; // Assuming response contains token and user data

      // Store the JWT token and user info in localStorage
      console.log("Saving to localStorage:", JSON.stringify(user), token); // Debugging
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(user));
      console.log("user saved :", localStorage.getItem("user"));
      console.log("token saved :", localStorage.getItem("token"));

      // Set a timeout to remove the user and token from localStorage after 10 minutes (600000 milliseconds)
      setTimeout(() => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        console.log('Token and user removed from localStorage after 10 minutes');
      }, 600000); // 10 minutes = 600000 milliseconds

      // Update the user context
      setUser(user);

      setMessage({ type: 'success', content: 'Login successful!' });
      console.log('Login response:', response.data);

      // Close the popup by calling onClose
      if (onClose) {
        onClose(); // Close the popup
      }
      
    } catch (error) {
      setMessage({
        type: 'error',
        content: error.response?.data?.message || 'Login failed. Please try again.',
      });
      console.error('Login error:', error.response?.data || error.message);
    }
  };

  const handleSignupSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await request('POST', '/api/auth/signup', signupData);
      setMessage({ type: 'success', content: 'Signup successful! You can now log in.' });
      console.log('Signup response:', response.data);
    } catch (error) {
      setMessage({
        type: 'error',
        content: error.response?.data?.message || 'Signup failed. Please try again.',
      });
      console.error('Signup error:', error.response?.data || error.message);
    }
  };

  const handleInputChange = (e, isSignup) => {
    const { name, value } = e.target;
    if (isSignup) {
      setSignupData((prevData) => ({ ...prevData, [name]: value }));
    } else {
      setLoginData((prevData) => ({ ...prevData, [name]: value }));
    }
  };

  return (
    <div className="container">
      <button className="close-button" onClick={onClose}>
        🗙
      </button>
      <section className="wrapper" ref={wrapperRef}>
        {message.content && (
          <div className={`message ${message.type}`}>
            {message.content}
          </div>
        )}

        <div className="form signup">
          <header onClick={handleSignupClick}>Signup</header>
          <form onSubmit={handleSignupSubmit}>
            <input
              type="text"
              name="name"
              placeholder="Full name"
              required
              onChange={(e) => handleInputChange(e, true)}
            />
            <input
              type="text"
              name="email"
              placeholder="Email address"
              required
              onChange={(e) => handleInputChange(e, true)}
            />
            <input
              type="password"
              name="password"
              placeholder="Password"
              required
              onChange={(e) => handleInputChange(e, true)}
            />
            <div className="checkbox">
              <input type="checkbox" id="signupCheck" />
              <label htmlFor="signupCheck">I accept all terms & conditions</label>
            </div>
            <input type="submit" value="Signup" />
          </form>
        </div>

        <div className="form login">
          <header onClick={handleLoginClick}>Login</header>
          <form onSubmit={handleLoginSubmit}>
            <input
              type="text"
              name="email"
              placeholder="Email address"
              required
              onChange={(e) => handleInputChange(e, false)}
            />
            <input
              type="password"
              name="password"
              placeholder="Password"
              required
              onChange={(e) => handleInputChange(e, false)}
            />
            <a href="#">Forgot password?</a>
            <input type="submit" value="Login" />
          </form>
        </div>
      </section>
    </div>
  );
};

export { Login };
