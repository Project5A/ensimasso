import axios from 'axios';

const instance = axios.create({
    baseURL: 'http://localhost:8080/api', // Set your base URL here
});

export default instance; 
