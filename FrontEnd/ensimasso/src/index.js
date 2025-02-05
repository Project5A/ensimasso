// index.js
import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import reportWebVitals from './reportWebVitals';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Navbar } from './components/Navbar/Navbar.jsx';
import Home from './pages/Home';
import Assos from './pages/Assos';
import Forum from './pages/Forum';
import Events from './pages/Events';
import About from './pages/About';
import { Profile } from './pages/Profile';
import { Footer } from './components/Footer/Footer';
import { AssociationPage } from './components/Assos/AssociationPage';
import { UserProvider } from './contexts/UserContext.js';
import {Chatbot} from './components/chatbot/Chatbot.jsx'; // Adjust the path as needed

const root = ReactDOM.createRoot(document.getElementById('root'));

root.render(
  <React.StrictMode>
    <UserProvider>
      <Router>
        <Navbar />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/assos" element={<Assos />} />
          <Route path="/forum" element={<Forum />} />
          <Route path="/events" element={<Events />} />
          <Route path="/about" element={<About />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/assos/bdlc" element={<AssociationPage />} />
        </Routes>
        <Footer />
        <Chatbot /> {/* Include the Chatbot component */}
      </Router>
    </UserProvider>
  </React.StrictMode>
);

reportWebVitals();
