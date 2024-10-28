// components/ui/label.tsx
import React from "react";

const Label: React.FC<{ htmlFor: string; children: React.ReactNode }> = ({ htmlFor, children }) => (
  <label htmlFor={htmlFor}  className="text-right text-gray-300">
    {children}
  </label>
);

export {Label};