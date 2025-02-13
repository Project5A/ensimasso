import React, { useState } from 'react';
import { FaComment, FaShare } from 'react-icons/fa';
import Reaction from './Reaction';

const PostCard = ({ post, onReact, onAddComment }) => {
  const [comment, setComment] = useState('');
  const [showComments, setShowComments] = useState(false);
  const [shareMsg, setShareMsg] = useState('');

  const toggleComments = () => setShowComments(!showComments);

  // Update handleCommentSubmit to force opening the comments section
  const handleCommentSubmit = (e) => {
    e.preventDefault();
    if (comment.trim()) {
      onAddComment(post.id, comment);
      setComment('');
      // Force the comment section to open upon submission
      setShowComments(true);
    }
  };

  // Fonction de partage (copie du lien du post dans le presse-papier)
  const handleShare = () => {
    const url = window.location.origin + `/posts/${post.id}`;
    navigator.clipboard.writeText(url).then(() => {
      setShareMsg('Lien copié !');
      setTimeout(() => setShareMsg(''), 2000);
    });
  };

  return (
    <div className="post-card">
      {/* En-tête du post */}
      <div className="post-header">
      <img src={post.user?.photo || 'assets/profile-circle.svg'} alt="Avatar" className="avatar" />
        <div className="post-header-info">
          <h3 className="author">{post.user?.name || 'Anonymous'}</h3>
          <span className="post-date">
            {new Date(post.createdAt).toLocaleDateString('fr-FR', { 
                year: 'numeric', 
                month: 'long', 
                day: 'numeric' 
            })}
          </span>
        </div>
      </div>

      {/* Titre (facultatif) */}
      {post.title && <h4 className="post-title">{post.title}</h4>}

      {/* Corps du post */}
      <div className="post-content">
        <p>{post.description}</p>
        {post.image && (
          <div className="media-container">
            {post.image.match(/\.(jpeg|jpg|gif|png)$/) ? (
              <img
                src={post.image}
                alt="Contenu du post"
                className="post-media"
                onError={(e) => e.target.style.display = 'none'}
              />
            ) : (
              <video controls className="post-media">
                <source src={post.image} type="video/mp4" />
                Votre navigateur ne supporte pas les vidéos.
              </video>
            )}
          </div>
        )}
      </div>

      {/* Pied de page avec réactions et actions */}
      <div className="post-footer">
        <div className="reactions-section">
          <Reaction
            reactions={post.reactions}
            onReact={(reaction) => onReact(post.id, reaction)}
          />
          <span className="total-reactions">
            {post.reactions
              ? Object.values(post.reactions).reduce((sum, count) => sum + count, 0)
              : 0}{' '}
            réactions
          </span>
        </div>
        <div className="actions">
          <button className="action-button" onClick={toggleComments}>
            <FaComment /> Commenter
          </button>
          <button className="action-button" onClick={handleShare}>
            <FaShare /> Partager
          </button>
          {shareMsg && <span className="share-msg">{shareMsg}</span>}
        </div>
      </div>

      {/* Section commentaires repliable */}
      {showComments && (
        <div className="comments-section">
          {/* Liste des commentaires (si disponible) */}
          {post.comments && post.comments.length > 0 ? (
            <ul className="comments-list">
              {post.comments.map((c) => (
                <li key={c.id} className="comment-item">
                  <img
                    src={c.user?.photo || 'assets/profile-circle.svg'}
                    alt="Avatar"
                    className="comment-avatar"
                  />
                  <div className="comment-content">
                    <span className="comment-author">{c.user?.name || 'Anonymous'}</span>
                    <span className="comment-text">{c.content}</span>
                  </div>
                </li>              
              ))}
            </ul>
          ) : (
            <p className="no-comments">Aucun commentaire pour le moment.</p>
          )}
          {/* Formulaire d'ajout d'un commentaire */}
          <form onSubmit={handleCommentSubmit} className="comment-form">
            <input
              type="text"
              placeholder="Écrire un commentaire..."
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
            <button type="submit">Publier</button>
          </form>
        </div>
      )}
    </div>
  );
};

export default PostCard;