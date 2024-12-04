import React from 'react';
import ReactDOM from 'react-dom/client';  // Keep this one for React 18+
import './index.css';
import reportWebVitals from './reportWebVitals';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Navbar } from './components/Navbar/Navbar.jsx';
import Home from './pages/Home';  
import Assos from './pages/Assos';  
import Forum from './pages/Forum';  
import Events from './pages/Events';  
import About from './pages/About';  
import { Footer } from './components/Footer/Footer';

import { UserProvider } from './contexts/UserContext.js'; // Adjust path as needed
import { ApolloClient, InMemoryCache, ApolloProvider } from '@apollo/client';

// Créer une instance ApolloClient pour interagir avec ton serveur GraphQL
const client = new ApolloClient({
  uri: 'http://localhost:4000/graphql',  // Remplacer par l'URI de ton serveur GraphQL
  cache: new InMemoryCache()
});

// Créer le root du rendu React
const root = ReactDOM.createRoot(document.getElementById('root'));

// Utiliser ApolloProvider pour fournir l'instance client à l'application
root.render(
  <React.StrictMode>
    <ApolloProvider client={client}>
      <UserProvider>
        <Router>
          <Navbar />
          <Routes> {/* Use Routes instead of Switch for React Router v6 */}
            <Route path="/" element={<Home />} />
            <Route path="/assos" element={<Assos />} />
            <Route path="/forum" element={<Forum/>} />
            <Route path="/events" element={<Events />} />
            <Route path="/about" element={<About />} />
          </Routes>
          <Footer />
        </Router>
      </UserProvider>
    </ApolloProvider>
  </React.StrictMode>
);

// Pour mesurer les performances de ton application
reportWebVitals();
