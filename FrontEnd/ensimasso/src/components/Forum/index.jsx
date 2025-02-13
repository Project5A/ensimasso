
import React, { useState, useEffect } from 'react';
import './index.css';
import axios from '../../axios';
import Modal from 'react-modal';
import { FaTimes, FaThumbsUp, FaThumbsDown } from 'react-icons/fa'; // Importing the icon

const ReactionButtons = ({ postId, likes, dislikes, onLike, onDislike }) => {
  return (
    <div>
      <button onClick={() => onLike(postId)}>Like ({likes})</button>
      <button onClick={() => onDislike(postId)}>Dislike ({dislikes})</button>
    </div>
  );
};

const CommentForm = ({ postId, onSubmit }) => {
  const [content, setContent] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(postId, content);
    setContent('');
  };

  return (
    <form onSubmit={handleSubmit}>
      <textarea value={content} onChange={(e) => setContent(e.target.value)} />
      <button type="submit">Ajouter une réponse</button>
    </form>
  );
};

const Forum = () => {
  const [newPost, setNewPost] = useState({
    title: '',
    description: '',
    author: '', // Corrected 'auteur' to 'author'
    date: '',
    response: 0, // Corrected 'reponse' to 'response'
    reaction: { like: 0, dislike: 0 },
  });
  const [posts, setPosts] = useState([]);
  const [modalIsOpen, setModalIsOpen] = useState(false);
  const [popupPostId, setPopupPostId] = useState(null); // State to manage the popup
  const [reactedPosts, setReactedPosts] = useState({}); // New state to track user reactions

  const [responses, setResponses] = useState({}); // State for handling responses

  useEffect(() => {
    const fetchPosts = async () => {
      try {
        const response = await axios.get('/api/posts');
        // Initializing posts with response counters set to 0
        const postsWithInitialResponses = response.data.map(post => ({
          ...post,
          response: 0, // Initializing response counter to 0
          reaction: post.reaction || { like: 0, dislike: 0 }, // Ensure reaction exists
        }));
        setPosts(postsWithInitialResponses);
      } catch (error) {
        console.error('Error fetching posts:', error);
      }
    };
    fetchPosts();
  }, []);

  const handleSubmit = async () => {
    if (!newPost.title || !newPost.description || !newPost.author) { // Corrected 'auteur' to 'author'
      console.error('All fields must be filled');
      return;
    }
    const postToSubmit = {
      ...newPost,
      date: new Date().toISOString().split('T')[0],
    };
    try {
      const response = await axios.post('/api/posts', postToSubmit);
      setPosts([...posts, response.data]);
      setNewPost({ title: '', description: '', author: '', date: '', response: 0, reaction: { like: 0, dislike: 0 } });
      setModalIsOpen(false);
    } catch (error) {
      console.error('Error creating post:', error);
    }
  };
  const handleResponseSubmit = async (id, responseText) => {
    if (!responseText) return;
    try {
      const response = await axios.post(`/api/posts/${id}/response`, { response: responseText }); // Updated to send response as JSON
      setPosts(posts.map(post => post.id === id ? { ...post, response: post.response + 1 } : post));
      setResponses({ ...responses, [id]: '' });
    } catch (error) {
      console.error('Error adding response:', error);
    }
  };
  const handleReaction = async (id, reactionType) => {
    try {
      const endpoint = reactionType === 'like' ? 'like' : 'dislike';
      const response = await axios.post(`/api/posts/${id}/${endpoint}`);
  
      const updatedPosts = posts.map((post) =>
        post.id === id
          ? { ...post, likes: response.data.likes, dislikes: response.data.dislikes }
          : post
      );
      setPosts(updatedPosts);
      setReactedPosts({ ...reactedPosts, [id]: reactionType });
    } catch (error) {
      console.error('Error adding reaction to post:', error);
    }
  };

  const closePopup = () => {
    setPopupPostId(null); // Close popup automatically
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
            <button onClick={() => setModalIsOpen(true)} className="modal-open-button">
              Ajouter votre poste
            </button>
            <Modal
              isOpen={modalIsOpen}
              onRequestClose={() => setModalIsOpen(false)}
              className="add-post-modal"
              overlayClassName="ReactModal__Overlay"
            >
              <button onClick={() => setModalIsOpen(false)} className="modal-close-button">
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
                value={newPost.author}
                onChange={(e) => setNewPost({ ...newPost, author: e.target.value })}
                className="modal-input"
              />

              <button onClick={handleSubmit} className="modal-submit-button">
                Confirmation de poste
              </button>
            </Modal>
          </div>

          {posts.map((post) => (
            <div key={post.id} className="post">
              <h2>{post.title}</h2>
              <p>{post.description}</p>
              <div className="post-info">
                <span>Auteur: {post.author}</span>
                <span>Date: {post.date}</span>
              </div>
              <div className="post-stats">
              <span>Réponses: {post.response}</span>
              <div className="responses-section">
                <input
                  type="text"
                  placeholder="Ajoutez une réponse..."
                  value={responses[post.id] || ''}
                  onChange={(e) => setResponses({ ...responses, [post.id]: e.target.value })}
                />
                <button onClick={() => handleResponseSubmit(post.id, responses[post.id])}>Répondre</button>
              </div>
              <ReactionButtons 
                        postId={post.id} 
                        likes={post.likes} // Utilisez post.likes directement
                        dislikes={post.dislikes} // Utilisez post.dislikes directement
                        onLike={handleReaction}
                        onDislike={handleReaction}
                      />             
                 </div>

              {popupPostId === post.id && (
                <div className="reaction-popup">
                  <div className="popup-content">
                    <button
                      onClick={() => {
                        handleReaction(post.id, 'like');
                        closePopup();
                      }}
                      className={reactedPosts[post.id] === 'like' ? 'selected' : ''}
                    >
                      J'aime
                    </button>
                    <button
                      onClick={() => {
                        handleReaction(post.id, 'dislike');
                        closePopup();
                      }}
                      className={reactedPosts[post.id] === 'dislike' ? 'selected' : ''}
                    >
                      Je n'aime pas
                    </button>
                  </div>
                </div>
              )}
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
