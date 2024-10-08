// src/pages/Assos.js

import React from 'react';
import { gql, useQuery } from '@apollo/client';
import styled from 'styled-components';

// Styled components
const AssosContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
`;

const AssoCard = styled.div`
  background-color: #f4f4f4;
  padding: 2rem;
  margin: 1rem 0;
  width: 80%;
  text-align: center;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
`;

// GraphQL query
const GET_ASSOS = gql`
  query GetAssos {
    associations {
      name
      description
      link
    }
  }
`;

const Assos = () => {
  const { loading, error, data } = useQuery(GET_ASSOS);

  if (loading) return <p>Loading...</p>;
  if (error) return <p>Error :(</p>;

  return (
    <AssosContainer>
      {data.associations.map((asso, index) => (
        <AssoCard key={index}>
          <h2>{asso.name}</h2>
          <p>{asso.description}</p>
          <button onClick={() => window.location.href = asso.link}>Go</button>
        </AssoCard>
      ))}
    </AssosContainer>
  );
};

export default Assos;
