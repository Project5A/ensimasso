import React, { createContext, useContext, useState } from 'react';

const UserContext = createContext();

export const useUser = () => useContext(UserContext);

export const UserProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);

  const login = async (username, password) => {
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      const data = await response.json();
      if (response.ok) {
        setToken(data.token);
        setUser({ username });
        localStorage.setItem('token', data.token);
      }
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
  };

  const validateToken = async () => {
    const storedToken = localStorage.getItem('token');
    if (!storedToken) return;
    try {
      const response = await fetch('/api/auth/validate', {
        headers: { Authorization: `Bearer ${storedToken}` },
      });
      if (response.ok) {
        setToken(storedToken);
        setUser({ username: 'user' }); // Replace with actual decoded token data
      }
    } catch {
      logout();
    }
  };

  React.useEffect(() => {
    validateToken();
  }, []);

  return (
    <UserContext.Provider value={{ user, login, logout }}>
      {children}
    </UserContext.Provider>
  );
};
