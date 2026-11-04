import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { GraphQLProvider } from './context/GraphQLContext'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <GraphQLProvider>
        <App />
      </GraphQLProvider>
    </BrowserRouter>
  </StrictMode>,
)
