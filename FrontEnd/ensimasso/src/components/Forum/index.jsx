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
  const [modalIsOpen, setModalIsOpen] = useState(false);
  const [newPost, setNewPost] = useState({
    title: '',
    description: '',
    image: null,
  });

  // Fetch posts when the component mounts.
  useEffect(() => {
    const fetchPosts = async () => {
      try {
        const response = await axios.get('/api/posts');
        setPosts(Array.isArray(response.data) ? response.data : [response.data]);
      } catch (error) {
        console.error('Error fetching posts:', error);
      }
    };
    fetchPosts();
  }, []);

  // Setup WebSocket connection for live updates.
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
            if (index !== -1) {
              const newPosts = [...prevPosts];
              newPosts[index] = updatedPost;
              return newPosts;
            } else {
              return [...prevPosts, updatedPost];
            }
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
      setPosts((prevPosts) => [...prevPosts, response.data]);
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
      
      // Update the post with the new comment data.
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
          <h3>Latest Posts</h3>
          <p>Check out recent updates.</p>
        </div>
        <div className="tags-section">
          <h3>Popular Tags</h3>
          <ul>
            <li>#javascript</li>
            <li>#react</li>
            <li>#design</li>
            <li>#innovation</li>
            <li>#tutorial</li>
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
          <h3>Meetups</h3>
          <p>Upcoming events.</p>
        </div>
        <div className="podcasts-section">
          <h3>Podcasts</h3>
          <p>Check out our shows.</p>
        </div>
      </div>
    </div>
  );
};

export default Forum;
