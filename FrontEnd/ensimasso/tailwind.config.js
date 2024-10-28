const {nextui} = require('@nextui-org/theme');

const flowbite = require("flowbite-react/tailwind");

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "// ...\n    flowbite.content()",
    "./node_modules/@nextui-org/theme/dist/**/*.{js,ts,jsx,tsx}"
  ],
  darkMode: "class",
  plugins: [// ...
    flowbite.plugin(),nextui()],
};