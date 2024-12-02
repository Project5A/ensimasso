import React, { useRef, useState } from 'react';
import { request } from '../axios_helper'; // Adjust the import path as needed
import './Login.css';

const Login = () => {
  const wrapperRef = useRef(null);
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
      const response = await request('POST', '/api/login', loginData);
      setMessage({ type: 'success', content: 'Login successful!' }); // Show success message
      console.log('Login response:', response.data);
      // Handle further login logic, e.g., save token, redirect, etc.
    } catch (error) {
      setMessage({
        type: 'error',
        content: error.response?.data?.message || 'Login failed. Please try again.',
      }); // Show error message
      console.error('Login error:', error.response?.data || error.message);
    }
  };

  const handleSignupSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await request('POST', '/api/signup', signupData);
      setMessage({ type: 'success', content: 'Signup successful! You can now log in.' }); // Show success message
      console.log('Signup response:', response.data);
      // Handle further signup logic, e.g., redirect to login
    } catch (error) {
      setMessage({
        type: 'error',
        content: error.response?.data?.message || 'Signup failed. Please try again.',
      }); // Show error message
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
