import React, { useState } from 'react';
import { useUser } from '../contexts/UserContext'; 
import { useNavigate } from 'react-router-dom'; 
import Sidebar from '../components/Sidebar/Sidebar'; 
import Profile from '../components/DashboardComponents/Profile'; 
import Posts from '../components/DashboardComponents/Posts'; 
import Events from '../components/DashboardComponents/Events'; 
import Historique from '../components/DashboardComponents/Historique'; 

const Dashboard = () => {
  const { user } = useUser(); 
  const navigate = useNavigate(); 

  const [activeComponent, setActiveComponent] = useState('profile'); // Default to 'profile' component

  if (!user) {
    console.log("going home !!");
    setTimeout(() => {
      navigate('/'); // Redirect to home or login
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
      default:
        return <Profile />; // Default component if none is selected
    }
  };

  return (
    <div className="main-content"> {/* Ajout de la classe main-content */}
      <div style={styles.pageContainer}>
        <Sidebar style={styles.sidebar} setActiveComponent={setActiveComponent} /> {/* Pass the setActiveComponent to Sidebar */}
        {renderComponent()} {/* Render the active component */} 
      </div>
    </div>
  );
};

export default Dashboard;
// Inline styles
const styles = {
  pageContainer: {
    display: 'flex',
    height: '100vh',
    backgroundColor: '#1a1a1a', // Darker background
    color: '#f0f0f0', // Light text for contrast
    },
  sidebar: {
    flex: '0 0 250px', // Fixed width for the sidebar
    backgroundColor: '#2a2a2a', // Slightly lighter than the page background
    height: '100%', // Full height of the page
  }
};
