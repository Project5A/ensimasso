// src/components/Forum/Reaction.jsx
import React from 'react';

const Reaction = ({ reactions = {}, onReact }) => {
  const reactionTypes = [
    { type: 'Like', emoji: '👍' },
    { type: 'Cheer', emoji: '👏🏻' },
    { type: 'Celebrate', emoji: '🎉' },
    { type: 'Appreciate', emoji: '✨' },
    { type: 'Smile', emoji: '🙂' },
    { type: 'dislike', emoji: '😵' },
  ];

  const handleClick = (reaction) => {
    if (onReact) onReact(reaction);
  };

  return (
    <div className="reaction-container">
      {reactionTypes.map((r) => (
        <button
          key={r.type}
          className="reaction-button"
          onClick={() => handleClick(r.type)}
        >
          <span className="emoji">{r.emoji}</span>
          <span className="count">{reactions[r.type] || 0}</span>
        </button>
      ))}
    </div>
  );
};

export default Reaction;
