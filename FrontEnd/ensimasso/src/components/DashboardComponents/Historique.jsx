import React, { useState } from 'react';
import { useUser } from '../../contexts/UserContext';
import { FaEye, FaTrash } from 'react-icons/fa'; // Assurez-vous d'avoir installé react-icons via npm

const Historique = () => {
  const { user, setUser } = useUser(); // user correspond à l'asso connecté
  const [gallery, setGallery] = useState(user.gallery || []);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [showPreview, setShowPreview] = useState(false);
  const [hoveredIndex, setHoveredIndex] = useState(null); // Indice de l'image survolée

  // Handler pour l'upload d'une nouvelle photo
  const handleAddPhoto = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    try {
      const formData = new FormData();
      formData.append('file', file);

      // Appel à votre API pour uploader la photo
      const response = await fetch(`http://localhost:8080/api/assos/${user.id}/gallery`, {
        method: 'POST',
        body: formData,
      });

      if (response.ok) {
        // On suppose que l'API retourne l'URL de la nouvelle photo
        const newPhotoUrl = await response.text();
        const updatedGallery = [...gallery, newPhotoUrl];
        setGallery(updatedGallery);
        setUser({ ...user, gallery: updatedGallery });
      } else {
        console.error("Erreur lors de l'upload");
      }
    } catch (error) {
      console.error('Erreur :', error);
    }
  };

  // Handler pour supprimer une photo
  const handleDeletePhoto = async (photoUrl) => {
    try {
      // Appel à l'API pour supprimer la photo
      const response = await fetch(`http://localhost:8080/api/assos/${user.id}/gallery`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ photoUrl }),
      });
      if (response.ok) {
        const updatedGallery = gallery.filter(url => url !== photoUrl);
        setGallery(updatedGallery);
        setUser({ ...user, gallery: updatedGallery });
      } else {
        console.error("Erreur lors de la suppression");
      }
    } catch (error) {
      console.error(error);
    }
  };

  // Handler pour afficher la prévisualisation d'une photo
  const handlePreviewPhoto = (photoUrl) => {
    setPreviewUrl(photoUrl);
    setShowPreview(true);
  };

  // Fermer la fenêtre de prévisualisation
  const closePreview = () => {
    setShowPreview(false);
    setPreviewUrl(null);
  };

  return (
    <div style={styles.content}>
      <h1 style={styles.title}>Gallery - {user.name}</h1>
      
      {/* Bouton pour ajouter une nouvelle photo */}
      <div style={styles.addButtonContainer}>
        <label htmlFor="photo-upload" style={styles.addButton}>
          Ajouter
        </label>
        <input 
          id="photo-upload" 
          type="file" 
          accept="image/*" 
          style={{ display: 'none' }} 
          onChange={handleAddPhoto} 
        />
      </div>

      {/* Galerie de photos */}
      <div style={styles.galleryContainer}>
        {gallery.map((photoUrl, index) => (
          <div 
            key={index} 
            style={styles.photoContainer}
            onMouseEnter={() => setHoveredIndex(index)}
            onMouseLeave={() => setHoveredIndex(null)}
          >
            <img src={photoUrl} alt={`Gallery ${index}`} style={styles.photo} />
            <div style={{ 
                ...styles.overlay, 
                opacity: hoveredIndex === index ? 1 : 0 
              }}>
              <button
                style={styles.iconButton}
                onClick={() => handlePreviewPhoto(photoUrl)}
                title="Visualiser"
              >
                <FaEye />
              </button>
              <button
                style={styles.iconButton}
                onClick={() => handleDeletePhoto(photoUrl)}
                title="Supprimer"
              >
                <FaTrash />
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Modal de prévisualisation */}
      {showPreview && (
        <div style={styles.modal} onClick={closePreview}>
          <div style={styles.modalContent} onClick={(e) => e.stopPropagation()}>
            <img src={previewUrl} alt="Preview" style={styles.previewImage} />
            <button onClick={closePreview} style={styles.closeButton}>X</button>
          </div>
        </div>
      )}
    </div>
  );
};

const styles = {
  content: {
    padding: '2rem',
    backgroundColor: '#1a1a1a',
    color: '#f0f0f0',
    minHeight: '100vh',
  },
  title: {
    textAlign: 'center',
    marginBottom: '1rem',
    fontSize: '2.5rem',
  },
  addButtonContainer: {
    display: 'flex',
    justifyContent: 'center',
    marginBottom: '2rem',
  },
  addButton: {
    padding: '0.5rem 1rem',
    backgroundColor: '#ff5722',
    color: '#fff',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '1.2rem',
  },
  galleryContainer: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
    gap: '1rem',
  },
  photoContainer: {
    position: 'relative',
    overflow: 'hidden',
    borderRadius: '10px',
  },
  photo: {
    width: '100%',
    display: 'block',
  },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    backgroundColor: 'rgba(0,0,0,0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'opacity 0.3s ease',
    zIndex: 2, // s'assure que l'overlay est au-dessus de l'image
  },
  iconButton: {
    background: 'none',
    border: 'none',
    color: '#fff',
    fontSize: '1.5rem',
    margin: '0 0.5rem',
    cursor: 'pointer',
  },
  modal: {
    position: 'fixed',
    top: 0,
    left: 0,
    width: '100vw',
    height: '100vh',
    backgroundColor: 'rgba(0,0,0,0.8)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  modalContent: {
    backgroundColor: 'transparent',
    padding: '0rem',
    borderRadius: '8px',
    position: 'relative',
    maxWidth: '60%',
    maxHeight: '60%',
  },
  previewImage: {
    borderRadius: '16px',
    width: 'auto',
    height: 'auto',
  },
  closeButton: {
    position: 'absolute',
    top: '10px',
    right: '10px',
    background: 'red',
    color: '#fff',
    border: 'none',
    borderRadius: '20px',
    padding: '0.3rem',
    cursor: 'pointer',
  },
};

export default Historique;
