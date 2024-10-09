import React from 'react';
import { Link } from 'react-router-dom';
import styled from 'styled-components';

const NavbarContainer = styled.nav`
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #333;
  padding: 1rem;
`;

const Logo = styled.div`
  font-size: 1.5rem;
  color: #fff;
  cursor: pointer;
`;

const NavMenu = styled.ul`
  display: flex;
  list-style: none;
  gap: 1.5rem;
`;

const NavItem = styled.li`
  color: #fff;
  cursor: pointer;
  &:hover {
    color: #ddd;
  }
`;

const Navbar = () => {
  return (
    <NavbarContainer>
      <Logo>EnsimAsso</Logo>
      <NavMenu>
        <NavItem><Link to="/">Accueil</Link></NavItem>
        <NavItem><Link to="/assos">Associations</Link></NavItem>
        <NavItem><Link to="/forum">Forum</Link></NavItem>
        <NavItem><Link to="/events">Events</Link></NavItem>
        <NavItem><Link to="/about">About</Link></NavItem>
        <NavItem><Link to="/login">Login</Link></NavItem>
      </NavMenu>
    </NavbarContainer>
  );
};

export { Navbar };

