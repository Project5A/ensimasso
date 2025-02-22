import axios from 'axios';
import React, { useState, useEffect } from 'react';
import { useUser } from '../../contexts/UserContext';

const PostsDashboard = () => {
  const { user } = useUser();
  const [posts, setPosts] = useState([]);
  const [editingPostId, setEditingPostId] = useState(null);
  const [formData, setFormData] = useState({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchPosts = async () => {
      try {
        const response = await axios.get('/api/posts');
        // Filtrer les posts de l'utilisateur courant
        const userPosts = response.data.filter(post => post.user?.id === user.id);
        setPosts(userPosts);
      } catch (error) {
        console.error('Erreur lors de la récupération des posts :', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPosts();
  }, [user]);

  const handleEdit = (post) => {
    setEditingPostId(post.id);
    setFormData({
      title: post.title,
      description: post.description,
      date: post.date,
      image: post.image || ''
    });
  };

  const handleCancel = () => {
    setEditingPostId(null);
    setFormData({});
  };

  const handleChange = (e) => {
    setFormData(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSave = async (postId) => {
    try {
      const response = await axios.put(`/api/posts/${postId}`, formData);
      setPosts(prevPosts =>
        prevPosts.map(post => (post.id === postId ? response.data : post))
      );
      setEditingPostId(null);
      setFormData({});
    } catch (error) {
      console.error('Erreur lors de la mise à jour du post :', error);
    }
  };

  const handleDelete = async (postId) => {
    if (!window.confirm("Voulez-vous vraiment supprimer ce post ?")) return;
    try {
      await axios.delete(`/api/posts/${postId}`);
      setPosts(prevPosts => prevPosts.filter(post => post.id !== postId));
    } catch (error) {
      console.error('Erreur lors de la suppression du post :', error);
    }
  };

  if (loading) return <div style={styles.loading}>Chargement des posts...</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.title}>Mes Posts</h1>
      {posts.length === 0 ? (
        <p style={styles.text}>Aucun post à afficher.</p>
      ) : (
        posts.map(post => (
          <div key={post.id} style={styles.postCard}>
            {editingPostId === post.id ? (
              <div>
                <input
                  type="text"
                  name="title"
                  value={formData.title}
                  onChange={handleChange}
                  style={styles.input}
                  placeholder="Titre"
                />
                <textarea
                  name="description"
                  value={formData.description}
                  onChange={handleChange}
                  style={styles.textarea}
                  placeholder="Description"
                />
                <input
                  type="text"
                  name="date"
                  value={formData.date}
                  onChange={handleChange}
                  style={styles.input}
                  placeholder="Date (ex. 2025-02-12)"
                />
                <input
                  type="text"
                  name="image"
                  value={formData.image}
                  onChange={handleChange}
                  style={styles.input}
                  placeholder="URL de l'image"
                />
                <div style={styles.buttonContainer}>
                  <button onClick={() => handleSave(post.id)} style={styles.primaryButton}>
                    Sauvegarder
                  </button>
                  <button onClick={handleCancel} style={styles.secondaryButton}>
                    Annuler
                  </button>
                </div>
              </div>
            ) : (
              <div>
                <h2 style={styles.postTitle}>{post.title}</h2>
                <p style={styles.postDescription}>{post.description}</p>
                <p style={styles.postDate}>Date : {post.date}</p>
                {post.image && <img src={post.image} alt="Post" style={styles.postImage} />}
                <div style={styles.buttonContainer}>
                  <button onClick={() => handleEdit(post)} style={styles.primaryButton}>
                    Modifier
                  </button>
                  <button onClick={() => handleDelete(post.id)} style={styles.secondaryButton}>
                    Supprimer
                  </button>
                </div>
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
};

const styles = {
  container: {
    padding: '2rem',
    backgroundColor: '#f0f2f5',
    minHeight: '100vh',
    fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif"
  },
  title: {
    fontSize: '2.5rem',
    textAlign: 'center',
    marginBottom: '2rem',
    color: '#333'
  },
  loading: {
    textAlign: 'center',
    padding: '2rem',
    fontSize: '1.2rem',
    color: '#666'
  },
  text: {
    textAlign: 'center',
    fontSize: '1.2rem',
    color: '#666'
  },
  postCard: {
    backgroundColor: '#fff',
    borderRadius: '10px',
    padding: '1.5rem',
    marginBottom: '1rem',
    boxShadow: '0 2px 6px rgba(0, 0, 0, 0.1)'
  },
  postTitle: {
    fontSize: '1.8rem',
    marginBottom: '0.5rem',
    color: '#333'
  },
  postDescription: {
    fontSize: '1.2rem',
    marginBottom: '0.5rem',
    color: '#555'
  },
  postDate: {
    fontSize: '1rem',
    color: '#888',
    marginBottom: '1rem'
  },
  postImage: {
    width: '100%',
    borderRadius: '8px',
    marginBottom: '1rem'
  },
  input: {
    width: '100%',
    padding: '0.8rem',
    marginBottom: '1rem',
    border: '1px solid #ccc',
    borderRadius: '8px',
    fontSize: '1rem'
  },
  textarea: {
    width: '100%',
    padding: '0.8rem',
    marginBottom: '1rem',
    border: '1px solid #ccc',
    borderRadius: '8px',
    fontSize: '1rem',
    minHeight: '100px'
  },
  buttonContainer: {
    display: 'flex',
    gap: '1rem'
  },
  primaryButton: {
    flex: 1,
    backgroundColor: '#3498db',
    color: '#fff',
    border: 'none',
    padding: '0.8rem',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '1rem'
  },
  secondaryButton: {
    flex: 1,
    backgroundColor: '#e74c3c',
    color: '#fff',
    border: 'none',
    padding: '0.8rem',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '1rem'
  }
};

export default PostsDashboard;
