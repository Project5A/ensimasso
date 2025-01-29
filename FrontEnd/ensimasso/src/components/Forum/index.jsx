import React, { useState, useEffect } from 'react';
import './index.css';
import axios from '../../axios';
import Modal from 'react-modal';
import { FaTimes } from 'react-icons/fa'; // Import de l'icône

const Forum = () => {
  const [newPost, setNewPost] = useState({
    title: '',
    description: '',
    auteur: '',
    date: '',
    reponse: 0,
    reaction: 0,
  });

  const [posts, setPosts] = useState([]);
  const [modalIsOpen, setModalIsOpen] = useState(false);

  useEffect(() => {
    const fetchPosts = async () => {
      try {
        const response = await axios.get('/api/posts');
        setPosts(response.data);
      } catch (error) {
        console.error('Error fetching posts:', error);
      }
    };
    fetchPosts();
  }, []);

  const handleSubmit = async () => {
    if (!newPost.title || !newPost.description || !newPost.auteur) {
      console.error('All fields must be filled');
      return;
    }
    const postToSubmit = {
      ...newPost,
      date: new Date().toISOString().split('T')[0],
    };

    try {
      const response = await axios.post('/api/posts', postToSubmit);
      console.log('Post created:', response.data);
      setPosts([...posts, response.data]); // Ajoute directement le nouveau post
      setNewPost({ title: '', description: '', auteur: '', date: '', reponse: 0, reaction: 0 });
      setModalIsOpen(false); // Ferme le modal après la soumission
    } catch (error) {
      console.error('Error creating post:', error);
    }
  };

  return (
    <>
      <div className="forum-container">
        <div className="left-sidebar mt-32">
          <div className="sidebar-section">
            <h3>Newest and Recent</h3>
            <p>Find the latest updates here.</p>
          </div>
          <div className="tags-section">
            <h3>Popular Tags</h3>
            <ul>
              <li>#javascript</li>
              <li>#bitcoin</li>
              <li>#design</li>
              <li>#innovation</li>
              <li>#tutorial</li>
            </ul>
          </div>
        </div>

        <div className="forum mt-32">
          <div className="add-post-form">
            <button 
              onClick={() => setModalIsOpen(true)} 
              className="modal-open-button"
            >
              Ajouter votre poste
            </button>
            <Modal 
              isOpen={modalIsOpen} 
              onRequestClose={() => setModalIsOpen(false)} 
              className="add-post-modal"
              overlayClassName="ReactModal__Overlay"
            >
              <button 
                onClick={() => setModalIsOpen(false)} 
                className="modal-close-button"
              >
                <FaTimes /> {/* Icône de fermeture */}
              </button>
              
              <input 
                type="text" 
                placeholder="Title" 
                value={newPost.title} 
                onChange={(e) => setNewPost({ ...newPost, title: e.target.value })} 
                className="modal-input" 
              />
              <textarea 
                placeholder="Description" 
                value={newPost.description} 
                onChange={(e) => setNewPost({ ...newPost, description: e.target.value })} 
                className="modal-textarea" 
              />
              <input 
                type="text" 
                placeholder="Auteur" 
                value={newPost.auteur} 
                onChange={(e) => setNewPost({ ...newPost, auteur: e.target.value })} 
                className="modal-input" 
              />
              
              <button 
                onClick={handleSubmit} 
                className="modal-submit-button"
              >
                Confirmation de poste
              </button>
            </Modal>
          </div>
          {posts.map((post) => (
            <div key={post.id} className="post">
              <h2>{post.title}</h2>
              <p>{post.description}</p>    
              <div className="post-info">
                <span>Auteur: {post.auteur}</span>
                <span>Date: {post.date}</span>
              </div>
              <div className="post-stats">
                <span>Réponses: {post.reponse}</span>
                <span>Réactions: {post.reaction}</span>
              </div>
            </div>
          ))}
        </div>

        <div className="right-sidebar mt-32">
          <div className="meetups-section">
            <h3>Meetups</h3>
            <p>Upcoming events and meetups.</p>
          </div>
          <div className="podcasts-section">
            <h3>Podcasts</h3>
            <p>Listen to industry insights.</p>
          </div>
        </div>
      </div>
    </>
  );
};

export default Forum;
