// components/ui/button.tsx
import React from "react";

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "outline";
  size?: "sm" | "md" | "lg" | "icon";
  className?: string;
};

const Button: React.FC<ButtonProps> = ({ children, variant = "primary", size = "md", className, ...props }) => {
  const baseStyles = "rounded transition-colors duration-300";
  
  const variantStyles =
    variant === "secondary"
      ? "bg-white/20 text-white hover:bg-white/30"
      : variant === "ghost"
      ? "bg-transparent text-gray-600 hover:bg-gray-100"
      : variant === "outline"
      ? "border border-gray-600 bg-transparent text-white hover:bg-white/20"
      : "bg-blue-500 text-white hover:bg-blue-600";

  const sizeStyles =
    size === "icon"
      ? "p-2" // small padding for icon buttons
      : size === "sm"
      ? "px-2 py-1 text-sm"
      : size === "lg"
      ? "px-6 py-3 text-lg"
      : "px-4 py-2 text-md";

  return (
    <button className={`${baseStyles} ${variantStyles} ${sizeStyles} ${className}`} {...props}>
      {children}
    </button>
  );
};

export {Button};