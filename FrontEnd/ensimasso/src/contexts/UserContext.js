import React, { createContext, useContext, useState, useCallback, useEffect } from "react";

const UserContext = createContext();

export const useUser = () => useContext(UserContext);

export const UserProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

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

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const token = localStorage.getItem("token");
        if (!token) {
          setLoading(false);
          return;
        }

        const response = await fetch("/api/users/me", {
          headers: { Authorization: `Bearer ${token}` },
        });

        if (response.ok) {
          const userData = await response.json();
          setUser(userData);
        }
      } catch (error) {
        console.error("Fetch user error:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, []);

  if (loading) {
    return <div style={{ textAlign: "center", padding: "2rem" }}>Chargement...</div>;
  }

  return (
    <UserContext.Provider value={{ user, setUser, login, logout }}>
      {children}
    </UserContext.Provider>
  );
};
