import axios from 'axios';

// Set default base URL
axios.defaults.baseURL = 'http://localhost:8080/';
axios.defaults.headers.post["Content-type"] = 'application/json';

// Add a request interceptor to include the token in the Authorization header
axios.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token'); // Retrieve token from localStorage
    if (token) {
      // If token exists, add it to Authorization header
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    // Handle request error
    return Promise.reject(error);
  }
);

// Modified request function
export const request = (method, url, data) => {
  return axios({
    method: method,
    url: url,
    data: data,
  });
};
