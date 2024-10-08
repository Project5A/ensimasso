import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';
import { BrowserRouter as Router, Route, Switch } from 'react-router-dom';
import Navbar from './components/Navbar';
import Home from './pages/Home';  
import Assos from './pages/Assos';  
import Forum from './pages/Forum';  
import Events from './pages/Events';  
import About from './pages/About';  
import Login from './pages/Login';  
import Footer from './components/Footer';  // N'oublie pas d'importer le Footer
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
      <Router>
        <Navbar />
        <Switch>
          <Route path="/" exact component={Home} />
          <Route path="/assos" component={Assos} />
          <Route path="/forum" component={Forum} />
          <Route path="/events" component={Events} />
          <Route path="/about" component={About} />
          <Route path="/login" component={Login} />
        </Switch>
        <Footer />
      </Router>
    </ApolloProvider>
  </React.StrictMode>
);

// Pour mesurer les performances de ton application
reportWebVitals();
