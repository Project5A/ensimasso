import React from 'react';
import { data } from './data.jsx';
import './index.css';

const Forum = () => {
  return (
    <>
    <img src={"./images/Forum_ensim.jpg"} alt="forum" className="img"/>
      <div className="forum">
        {data.map((post) => (
          <div key={post.id} className="post">
            <h2>{post.title}</h2>
            <p>{post.description}</p>
            <img src={post.image} alt={`Image for ${post.title}`} />
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
    </>
  );
};

export default Forum;
