import { NavLink, Outlet } from 'react-router-dom'
import { useGraphQL } from '../context/GraphQLContext'
import ChatPanel from './ChatPanel'

function NavItem({ to, children }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `px-3 py-2 text-sm uppercase tracking-wide ${isActive ? 'bg-spring-green text-white' : 'text-spring-light-grey hover:bg-spring-green hover:text-white'}`
      }
    >
      {children}
    </NavLink>
  )
}

export default function Layout() {
  const { available } = useGraphQL()
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b-4 border-spring-green bg-spring-brown">
        <nav className="mx-auto flex max-w-5xl flex-wrap items-center gap-1 px-4 py-2">
          <NavLink to="/" className="mr-4 flex items-center">
            <img src="/spring-logo.svg" alt="Spring" className="h-8" />
          </NavLink>
          <NavItem to="/">Home</NavItem>
          {available('findOwners') && <NavItem to="/owners">Find owners</NavItem>}
          {available('listVets') && <NavItem to="/vets">Veterinarians</NavItem>}
        </nav>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="bg-spring-brown py-4 text-center">
        <img src="/spring-logo.svg" alt="Spring" className="mx-auto h-6 opacity-70" />
      </footer>
      <ChatPanel />
    </div>
  )
}
