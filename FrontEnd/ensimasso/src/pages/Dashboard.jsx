// Dashboard.jsx
import React, { useState } from 'react';
import { useUser } from '../contexts/UserContext'; 
import { useNavigate } from 'react-router-dom'; 
import Sidebar from '../components/Sidebar/Sidebar'; 
import Profile from '../components/DashboardComponents/Profile'; 
import Posts from '../components/DashboardComponents/Posts'; 
import Events from '../components/DashboardComponents/Events'; 
import Historique from '../components/DashboardComponents/Historique';
import Adhesion from '../components/DashboardComponents/Adhesion'; // Import du nouveau composant

const Dashboard = () => {
  const { user } = useUser(); 
  const navigate = useNavigate(); 

  const [activeComponent, setActiveComponent] = useState('profile'); // Composant par défaut

  if (!user) {
    console.log("going home !!");
    setTimeout(() => {
      navigate('/'); // Redirection si l'utilisateur n'est pas connecté
    }, 3000);
  }

  const renderComponent = () => {
    switch (activeComponent) {
      case 'profile':
        return <Profile />;
      case 'posts':
        return <Posts />;
      case 'events':
        return <Events />;
      case 'historique':
      case 'gallery':
        return <Historique />;
      case 'adhesion':
        return <Adhesion />;
      default:
        return <Profile />;
    }
  };

  return (
    <div className="main-content">
      <div style={styles.pageContainer}>
        <Sidebar setActiveComponent={setActiveComponent} />
        {renderComponent()}
      </div>
    </div>
  );
};

export default Dashboard;

const styles = {
  pageContainer: {
    display: 'flex',
    height: '100vh',
    backgroundColor: '#1a1a1a',
    color: '#f0f0f0',
  },
  sidebar: {
    flex: '0 0 250px',
    backgroundColor: '#2a2a2a',
    height: '100%',
  }
};
