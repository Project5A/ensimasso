import React, { useState, useEffect } from 'react';

import './index.css';

import axios from '../../axios';



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



  useEffect(() => {

    const fetchPosts = async () => {

      try {

        const response = await axios.get('/posts');

        setPosts(response.data);

      } catch (error) {

        console.error('Error fetching posts:', error);

      }

    };

    fetchPosts();

  }, []);



  const handleSubmit = async (event) => {

    event.preventDefault();

    const postToSubmit = {

      title: newPost.title,

      description: newPost.description,

      auteur: newPost.auteur,

      date: new Date().toISOString().split('T')[0],

      reponse: 0,

      reaction: 0,

    };



    try {

      const response = await axios.post('/posts', postToSubmit);

      console.log('Post created:', response.data);

      setPosts([...posts, response.data]);

      setNewPost({ title: '', description: '', auteur: '', date: '', reponse: 0, reaction: 0 });

    } catch (error) {

      console.error('Error creating post:', error);

    }

  };



  return (

    <>

      <div className="header-image">

        <img src={"./images/Forum_ensim.jpg"} alt="forum" className="img"/>

      </div>



      <div className="forum-container">

        <div className="left-sidebar">

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



        <div className="forum">

          <form className="add-post-form" onSubmit={handleSubmit}>

            <input type="text" placeholder="Title" value={newPost.title} onChange={(e) => setNewPost({ ...newPost, title: e.target.value })} />

            <textarea placeholder="Description" value={newPost.description} onChange={(e) => setNewPost({ ...newPost, description: e.target.value })} />

            <button type="submit">Ajouter votre poste</button>

          </form>

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



        <div className="right-sidebar">

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




