import axios from 'axios';

const instance = axios.create({
  baseURL: 'https://ensimasso-back-fdaddxedbhcehpa8.francecentral-01.azurewebsites.net', 
});

// Automatically attach token if available
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
}, (error) => {
  return Promise.reject(error);
});

export default instance;
