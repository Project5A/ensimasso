
const flowbite = require("flowbite-react/tailwind");

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "// ...\n    flowbite.content()",
  ],
  darkMode: "class",
  plugins: [// ...
    flowbite.plugin()],
};