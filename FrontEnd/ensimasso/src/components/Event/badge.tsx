// components/ui/badge.tsx
import React from "react";

const Badge: React.FC<{ variant?: string; children: React.ReactNode }> = ({ variant = "default", children }) => (
  <span className={`mb-2 bg-white/10 text-white px-3 py-1 rounded-full ${variant === "default" ? "bg-gray-200" : "bg-blue-500 text-white"}`}>
    {children}
  </span>
);

export {Badge};
