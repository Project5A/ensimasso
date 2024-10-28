// components/ui/input.tsx
import React from "react";

const Input: React.FC<React.InputHTMLAttributes<HTMLInputElement>> = (props) => (
  <input className="border rounded p-2" {...props} />
);

export {Input};