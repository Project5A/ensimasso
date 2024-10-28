// components/ui/card.tsx
import React from "react";

const Card: React.FC<{ className?: string; children: React.ReactNode }> = ({ className, children }) => (
  <div className={`border rounded shadow-lg ${className}`}>{children}</div>
);

const CardHeader: React.FC<{ children: React.ReactNode }> = ({ children }) => <div className="p-0 relative">{children}</div>;

const CardContent: React.FC<{ children: React.ReactNode }> = ({ children }) => <div className="p-4">{children}</div>;

const CardTitle: React.FC<{ children: React.ReactNode }> = ({ children }) => <h3 className="text-xl mb-2 text-white">{children}</h3>;

const CardDescription: React.FC<{ children: React.ReactNode }> = ({ children }) => <p className="flex items-center text-gray-300 mb-2">{children}</p>;

const CardFooter: React.FC<{ children: React.ReactNode }> = ({ children }) => <div className="border-t p-4">{children}</div>;

export {Card};
export {CardHeader};
export {CardContent};
export {CardTitle};
export {CardDescription};
export {CardFooter};