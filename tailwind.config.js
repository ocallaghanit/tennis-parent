/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "api-tennis-adapter/src/main/resources/templates/**/*.html",
    "api-tennis-adapter/src/main/resources/static/**/*.js"
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          bg: "#0b0d12",
          muted: "#1b1f2a",
          text: "#e6e8ee",
          accent: "#38bdf8",   // sky-400
          ok: "#34d399",       // emerald-400
          warn: "#f59e0b",     // amber-500
          err: "#f43f5e"       // rose-500
        }
      }
    }
  },
  plugins: []
};


