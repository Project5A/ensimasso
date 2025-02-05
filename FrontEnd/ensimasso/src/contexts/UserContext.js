import React, { createContext, useContext, useState, useCallback, useEffect } from "react";

const UserContext = createContext();

export const useUser = () => useContext(UserContext);

export const UserProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const storedUser = JSON.parse(localStorage.getItem("user"));
    const storedToken = localStorage.getItem("token");

    if (storedUser && storedToken) {
      setUser(storedUser);
    }

    setLoading(false);
  }, []);

  const login = async (email, password) => {
    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      const data = await response.json();
      console.log("Login Response:", data); // Debugging

      if (response.ok && data?.user && data?.token) {
        console.log("Saving to localStorage:", JSON.stringify(data.user), data.token); // Debugging
        localStorage.setItem("user", JSON.stringify(data.user)); // Store user info
        localStorage.setItem("token", data.token); // Store token

        setUser(data.user); // Update React state
      } else {
        console.error("Invalid login response:", data);
      }
    } catch (error) {
      console.error("Login failed:", error);
    }
  };

  const logout = useCallback(() => {
    setUser(null);
    localStorage.removeItem("user");
    localStorage.removeItem("token"); // Remove token too
  }, []);

  if (loading) return <p>Loading...</p>;

  return (
    <UserContext.Provider value={{ user, setUser, login, logout }}>
      {children}
    </UserContext.Provider>
  );
};
