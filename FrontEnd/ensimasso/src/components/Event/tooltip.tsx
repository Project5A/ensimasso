// components/ui/tooltip.tsx
import React, { useState } from "react";

const TooltipProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => <div>{children}</div>;

const TooltipContent: React.FC<{ children: React.ReactNode }> = ({ children }) => <div>{children}</div>;

type TooltipProps = {
  title?: string;
  children: React.ReactNode;
};

export const Tooltip: React.FC<TooltipProps> = ({ title, children }) => {
  const [visible, setVisible] = useState(false);

  return (
    <div
      className="relative inline-block"
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
    >
      {children}
      {visible && title && (
        <div className="absolute left-1/2 transform -translate-x-1/2 mt-2 px-2 py-1 bg-black text-white text-xs rounded shadow-lg">
          {title}
        </div>
      )}
    </div>
  );
};

type TooltipTriggerProps = {
  children: React.ReactElement;
  asChild?: boolean;
};

const TooltipTrigger: React.FC<TooltipTriggerProps> = ({ children, asChild = false }) =>
  asChild ? React.cloneElement(children) : <span className="cursor-pointer">{children}</span>;

export {TooltipTrigger};
export {TooltipProvider};
export {TooltipContent};

