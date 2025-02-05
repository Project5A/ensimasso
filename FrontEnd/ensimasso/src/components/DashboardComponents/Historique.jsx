import { useUser } from '../../contexts/UserContext'; // Import the UserContext


const Historique =  () => {
    const { user } = useUser(); // Access the current user and setUser from context

    return (
        <div style={styles.content}>
            <h1 style={styles.title}>Welcome histories, {user.name}!</h1>
            <div style={styles.infoBox}>
            <p style={styles.text}>
                <strong>Name:</strong> {user.name}
            </p>
            <p style={styles.text}>
                <strong>Email:</strong> {user.email}
            </p>
            {/* Add more user details here if available */}
            </div>
        </div>
    );
}

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
    },
    content: {
    flex: 1, // Remaining space for the main content
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '2rem',
    },
    title: {
    fontSize: '3rem', // Larger title
    marginBottom: '2rem',
    fontWeight: 'bold',
    textAlign: 'center',
    },
    infoBox: {
    border: '1px solid #444', // Subtle border
    borderRadius: '12px',
    padding: '2rem',
    backgroundColor: '#2a2a2a', // Slightly lighter than the background
    boxShadow: '0 6px 12px rgba(0, 0, 0, 0.3)',
    marginBottom: '2rem',
    width: '800px',
    },
    text: {
    fontSize: '1.5rem', // Larger text for details
    margin: '0.5rem 0', // Add spacing between lines
    },
    button: {
    padding: '1rem 2rem', // Larger button
    fontSize: '1.2rem', // Bigger text on the button
    color: '#fff',
    backgroundColor: '#ff5722', // Brighter button color
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    transition: 'background-color 0.3s ease',
    },
};

export default Historique;