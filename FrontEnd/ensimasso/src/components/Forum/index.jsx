import React from 'react';
import { data } from './data.jsx';
import './index.css';

const Forum = () => {
  return (
    <>

      {/* Full-width Header Image */}
      <div className="header-image">
        <img src={"./images/Forum_ensim.jpg"} alt="forum" className="img"/>
      </div>

      {/* Main Content with Sidebars */}
      <div className="forum-container">
        {/* Left Sidebar */}
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
          {/* Additional sidebar sections can be added here */}
        </div>

        {/* Main Forum Content */}
        <div className="forum">
          {data.map((post) => (
            <div key={post.id} className="post">
              <h2>{post.title}</h2>
              <p>{post.description}</p>
              <img src={post.image} alt={`${post.title}`} />
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

        {/* Right Sidebar */}
        <div className="right-sidebar">
          <div className="meetups-section">
            <h3>Meetups</h3>
            <p>Upcoming events and meetups.</p>
          </div>
          <div className="podcasts-section">
            <h3>Podcasts</h3>
            <p>Listen to industry insights.</p>
          </div>
          {/* Additional sidebar sections can be added here */}
        </div>
      </div>
    </>
  );
};

export default Forum;
