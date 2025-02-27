// index.js
import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import reportWebVitals from './reportWebVitals';
import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';
import { Navbar } from './components/Navbar/Navbar.jsx';
import Home from './pages/Home';  
import Assos from './pages/Assos';  
import Forum from './pages/Forum';  
import Events from './pages/Events';  
import About from './pages/About';  
import Dashboard from './pages/Dashboard';  
import { Footer } from './components/Footer/Footer';
import { AssociationPage } from './components/Assos/AssociationPage';
import { UserProvider } from './contexts/UserContext.js';
import { Chatbot } from './components/chatbot/Chatbot.jsx';

// Layout component that conditionally renders the Footer
const Layout = ({ children }) => {
  const location = useLocation();
  const showFooter = location.pathname !== '/dashboard'; // Hide Footer on Dashboard
  return (
    <>
      {children}
      {showFooter && <Footer />}
      <Chatbot />
    </>
  );
};

const App = () => (
  <UserProvider>
    <Router>
      <Navbar />
      <Layout>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/assos" element={<Assos />} />
          <Route path="/forum" element={<Forum />} />
          <Route path="/events" element={<Events />} />
          <Route path="/about" element={<About />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/assos/bdlc" element={<AssociationPage />} />
        </Routes>
      </Layout>
    </Router>
  </UserProvider>
);

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

reportWebVitals();
