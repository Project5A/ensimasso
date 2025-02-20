import React, { useState, useEffect } from 'react';
import Modal from 'react-modal';
import { FaTimes } from 'react-icons/fa';
import axios from '../../axios';
import PostCard from './PostCard';
import { useUser } from '../../contexts/UserContext';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import './index.css';

Modal.setAppElement('#root');

const Forum = () => {
  const { user } = useUser();
  const [posts, setPosts] = useState([]);
  const [tags, setTags] = useState([]);
  const [topUsers, setTopUsers] = useState([]);
  const [modalIsOpen, setModalIsOpen] = useState(false);
  const [newPost, setNewPost] = useState({
    title: '',
    description: '',
    image: null,
  });

  // Fonction pour extraire les mots les plus fréquents
  const extractTags = (posts) => {
    const stopWords = new Set(['le', 'la', 'les', 'un', 'une', 'et', 'de', 'des', 'du', 'à', 'pour', 'en', 'avec', 'est', 'au', 'sur', 'dans']);
    const wordCount = {};

    posts.forEach((post) => {
      const text = `${post.title} ${post.description} ${post.comments}`.toLowerCase();
      const words = text.match(/\b[a-zA-ZÀ-ÿ0-9']+\b/g);
      
      if (words) {
        words.forEach((word) => {
          if (!stopWords.has(word) && word.length > 2) {
            wordCount[word] = (wordCount[word] || 0) + 1;
          }
        });
      }
    });

    const sortedTags = Object.entries(wordCount)
      .sort((a, b) => b[1] - a[1]) // Trier par fréquence
      .slice(0, 5) // Prendre les 5 mots les plus fréquents
      .map(([word]) => `#${word}`);

    setTags(sortedTags);
  };

  const extractTopUsers = (posts) => {
    const userActivity = {};
  
    posts.forEach((post) => {
      // Vérifie si l'utilisateur existe et a un nom
      const author = post.user?.name || 'Anonyme';
      userActivity[author] = (userActivity[author] || 0) + 1;
  
      // Compter les commentaires par utilisateur
      if (post.comments) {
        post.comments.forEach((comment) => {
          const commentAuthor = comment.user?.name || 'Anonyme';
          userActivity[commentAuthor] = (userActivity[commentAuthor] || 0) + 1;
        });
      }
    });
  
    // Trier les utilisateurs par activité (nombre de posts + commentaires)
    const sortedUsers = Object.entries(userActivity)
      .sort((a, b) => b[1] - a[1]) // Trier par activité décroissante
      .slice(0, 5) // Prendre les 5 plus actifs
      .map(([username, count]) => ({ username, count }));
  
    setTopUsers(sortedUsers);
  };
  
  

  useEffect(() => {
    const fetchPosts = async () => {
      try {
        const response = await axios.get('/api/posts');
        const fetchedPosts = Array.isArray(response.data) ? response.data.reverse() : [response.data];
        setPosts(fetchedPosts);
        extractTags(fetchedPosts);  // Met à jour les tags
        extractTopUsers(fetchedPosts); // Met à jour les utilisateurs actifs
      } catch (error) {
        console.error('Error fetching posts:', error);
      }
    };
    fetchPosts();
  }, []);
  
  
  // WebSocket pour mises à jour en temps réel
  useEffect(() => {
    const socket = new SockJS('http://localhost:8080/ws');
    const stompClient = new Client({
      webSocketFactory: () => socket,
      debug: (str) => console.log(str),
      onConnect: () => {
        stompClient.subscribe('/topic/posts', (message) => {
          const updatedPost = JSON.parse(message.body);
          setPosts((prevPosts) => {
            const index = prevPosts.findIndex((p) => p.id === updatedPost.id);
            let newPosts;
            if (index !== -1) {
              newPosts = [...prevPosts];
              newPosts[index] = updatedPost;
            } else {
              newPosts = [updatedPost, ...prevPosts];
            }
            extractTags(newPosts); // Mettre à jour les tags dynamiquement
            return newPosts;
          });
        });
      },
    });
    stompClient.activate();
    return () => stompClient.deactivate();
  }, []);

  // Handle file input change.
  const handleMediaChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setNewPost({ ...newPost, image: e.target.files[0] });
    }
  };

  // Handle new post submission.
  const handleSubmit = async () => {
    if (!newPost.title || !newPost.description) {
      console.error('All fields must be filled');
      return;
    }
    if (!user) {
      console.error('You must be logged in to create a post');
      return;
    }

    const formData = new FormData();
    formData.append('title', newPost.title);
    formData.append('description', newPost.description);
    formData.append('author', user.username || 'Anonymous');
    formData.append('date', new Date().toISOString().split('T')[0]);
    if (newPost.image) {
      formData.append('image', newPost.image);
    } else {
      formData.append('image', 'https://example.com/default-image.jpg');
    }

    try {
      const response = await axios.post('/api/posts', formData, {
        headers: { 
          'Content-Type': 'multipart/form-data',
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
      });
      setPosts((prevPosts) => [response.data, ...prevPosts]); // Ajoute en haut
      setModalIsOpen(false);
      setNewPost({ title: '', description: '', image: null });
    } catch (error) {
      console.error('Error creating post:', error);
    }
  };

  // Handle adding a comment.
  const handleAddComment = async (postId, commentText) => {
    try {
      const payload = { 
        response: commentText, 
        author: user ? user.username : 'Anonymous' 
      };
      
      const response = await axios.post(`/api/posts/${postId}/response`, payload);
      
      setPosts(prevPosts => prevPosts.map(post => 
        post.id === postId ? { ...response.data, comments: response.data.comments } : post
      ));
    } catch (error) {
      console.error('Error adding comment:', error);
    }
  };

  // Handle adding a reaction.
  const handleReaction = async (postId, reactionType) => {
    try {
      const response = await axios.post(`/api/posts/${postId}/react`, { reaction: reactionType });
      setPosts((prevPosts) =>
        prevPosts.map((post) =>
          post.id === postId ? { ...post, reactions: response.data } : post
        )
      );
    } catch (error) {
      console.error('Error adding reaction:', error);
    }
  };

  return (
    <div className="forum-container">
      {/* Left Sidebar */}
      <div className="left-sidebar mt-32">
        <div className="sidebar-section">
          <h3>Top Tags</h3>
          <ul>
            {tags.length > 0 ? tags.map((tag, index) => <li key={index}>{tag}</li>) : <li>Aucun tag</li>}
          </ul>
        </div>
      </div>

      {/* Main Forum Section */}
      <div className="forum mt-32">
        <div className="add-post-form">
          <button onClick={() => setModalIsOpen(true)} className="modal-open-button">
            Add a Post
          </button>
          <Modal
            isOpen={modalIsOpen}
            onRequestClose={() => setModalIsOpen(false)}
            className="add-post-modal"
            overlayClassName="ReactModal__Overlay"
          >
            <button onClick={() => setModalIsOpen(false)} className="modal-close-button">
              <FaTimes />
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
              type="file"
              accept="image/*,video/*"
              onChange={handleMediaChange}
              className="modal-input"
            />
            <button onClick={handleSubmit} className="modal-submit-button">
              Publish
            </button>
          </Modal>
        </div>

        {/* Render each post */}
        {posts.map((post) => (
          <PostCard
            key={post.id}
            post={post}
            onReact={handleReaction}
            onAddComment={handleAddComment}
          />
        ))}
      </div>

      {/* Right Sidebar */}
      <div className="right-sidebar mt-32">
        <div className="meetups-section">
          <h3>Top Users</h3>
          <ul>
            {topUsers.length > 0 ? (
              topUsers.map((user, index) => (
                <li key={index}>
                  {user.username} {user.count} 
                </li>
              ))
            ) : (
              <li>Aucun utilisateur actif</li>
            )}
          </ul>
        </div>
      </div>
    </div>
  );
};

export default Forum;
