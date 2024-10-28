// components/ui/textarea.tsx
import React from "react";

const Textarea: React.FC<React.TextareaHTMLAttributes<HTMLTextAreaElement>> = (props) => (
  <textarea className="border rounded p-2" {...props} />
);

export {Textarea};