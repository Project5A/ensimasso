// components/ui/dialog.tsx
import React from "react";

const Dialog: React.FC<{ open: boolean; onOpenChange: (open: boolean) => void; children: React.ReactNode }> = ({ open, children }) => {
  if (!open) return null;
  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50">
      {children}
    </div>
  );
};

const DialogContent: React.FC<{ className?: string; children: React.ReactNode }> = ({ className, children }) => (
  <div className={`bg-white rounded p-4 ${className}`}>{children}</div>
);

const DialogHeader: React.FC<{ children: React.ReactNode }> = ({ children }) => <div className="mb-4">{children}</div>;

const DialogTitle: React.FC<{ children: React.ReactNode }> = ({ children }) => <h2 className="text-2xl font-bold">{children}</h2>;

const DialogDescription: React.FC<{ children: React.ReactNode }> = ({ children }) => <p className="text-gray-300">{children}</p>;

const DialogFooter: React.FC<{ children: React.ReactNode }> = ({ children }) => <div className="mt-4">{children}</div>;

type DialogCloseProps = {
  asChild?: boolean;
  onClick?: () => void;
  className?: string;
  children: React.ReactNode;
};

const DialogClose: React.FC<DialogCloseProps> = ({ asChild = false, onClick, className, children }) => {
  const closeButton = (
    <button className={`absolute top-2 right-2 ${className}`} onClick={onClick}>
      {children}
    </button>
  );

  return asChild ? <>{children}</> : closeButton;
};

export {Dialog};
export {DialogContent};
export {DialogHeader};
export {DialogTitle};
export {DialogDescription};
export {DialogFooter};
export {DialogClose};