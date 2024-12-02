import React, { useRef } from 'react';
import './Login.css';

const Login = () => {
  const wrapperRef = useRef(null);

  const handleLoginClick = () => {
    if (wrapperRef.current) {
      wrapperRef.current.classList.add('active');
    }
  };

  const handleSignupClick = () => {
    if (wrapperRef.current) {
      wrapperRef.current.classList.remove('active');
    }
  };

  return (
    <div className="container">
      <section className="wrapper" ref={wrapperRef}>
        <div className="form signup">
          <header onClick={handleSignupClick}>Signup</header>
          <form action="">
            <input type="text" placeholder="Full name" required />
            <input type="text" placeholder="Email address" required />
            <input type="password" placeholder="Password" required />
            <div className="checkbox">
              <input type="checkbox" id="signupCheck" />
              <label htmlFor="signupCheck">I accept all terms & conditions</label>
            </div>
            <input type="submit" value="Signup" />
          </form>
        </div>

        <div className="form login">
          <header onClick={handleLoginClick}>Login</header>
          <form action="">
            <input type="text" placeholder="Email address" required />
            <input type="password" placeholder="Password" required />
            <a href="#">Forgot password?</a>
            <input type="submit" value="Login" />
          </form>
        </div>
      </section>
    </div>
  );
};

export { Login };
