// Chatbot.js
import React, { useEffect } from 'react';

const Chatbot = () => {
  useEffect(() => {
    // Function to load the Chatbase script
    const loadChatbase = () => {
      if (!window.chatbase || window.chatbase("getState") !== "initialized") {
        window.chatbase = (...args) => {
          if (!window.chatbase.q) {
            window.chatbase.q = [];
          }
          window.chatbase.q.push(args);
        };
        window.chatbase = new Proxy(window.chatbase, {
          get(target, prop) {
            if (prop === "q") {
              return target.q;
            }
            return (...args) => target(prop, ...args);
          },
        });
      }

      const script = document.createElement("script");
      script.src = "https://www.chatbase.co/embed.min.js";
      script.id = "BwG_oTHzIz8UtxOS-K0b2"; // Replace with your Chatbase ID
      script.domain = "www.chatbase.co";
      document.body.appendChild(script);
    };

    // Load the Chatbase script
    loadChatbase();
  }, []);

  return null; // This component doesn't render anything visible
};

export {Chatbot};
