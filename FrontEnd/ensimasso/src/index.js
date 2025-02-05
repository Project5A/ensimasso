import React from 'react';
import ReactDOM from 'react-dom/client'; // Keep this one for React 18+
import './index.css';
import reportWebVitals from './reportWebVitals';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Navbar } from './components/Navbar/Navbar.jsx';
import Home from './pages/Home';  
import Assos from './pages/Assos';  
import Forum from './pages/Forum';  
import Events from './pages/Events';  
import About from './pages/About';  
import Dashboard from './pages/Dashboard';  
import { Footer } from './components/Footer/Footer';
import { AssociationPage } from './components/Assos/AssociationPage';

import { UserProvider } from './contexts/UserContext.js'; // Adjust path as needed

// Créer le root du rendu React
const root = ReactDOM.createRoot(document.getElementById('root'));

root.render(
  <React.StrictMode>
    <UserProvider>
      <Router>
        <Navbar />
        <Routes> {/* Use Routes instead of Switch for React Router v6 */}
          <Route path="/" element={<Home />} />
          <Route path="/assos" element={<Assos />} />
          <Route path="/forum" element={<Forum/>} />
          <Route path="/events" element={<Events />} />
          <Route path="/about" element={<About />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/assos/bdlc" element={<AssociationPage />} />
        </Routes>
        <Footer />
      </Router>
    </UserProvider>
  </React.StrictMode>
);

// Pour mesurer les performances de ton application
reportWebVitals();
