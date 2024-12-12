import React, { createContext, useContext, useState, useCallback } from 'react';

const UserContext = createContext();

export const useUser = () => useContext(UserContext);

export const UserProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token') || null);

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
      } else {
        console.error('Login failed:', data.message || 'Unknown error');
      }
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
  }, []);

  const validateToken = useCallback(async () => {
    const storedToken = localStorage.getItem('token');
    if (!storedToken) return;
    try {
      const response = await fetch('/api/auth/validate', {
        headers: { Authorization: `Bearer ${storedToken}` },
      });
      if (response.ok) {
        setToken(storedToken); // Retain token in state
        setUser({ username: 'user' }); // Replace with actual decoded token data
      } else {
        logout();
      }
    } catch (error) {
      logout();
    }
  }, [logout]);

  React.useEffect(() => {
    validateToken();
  }, [validateToken]);

  return (
    <UserContext.Provider value={{ user, token, login, logout }}>
      {children}
    </UserContext.Provider>
  );
};
