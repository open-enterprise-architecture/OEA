import{n as e}from"./rolldown-runtime-sJBDdmOt.js";import{n as t}from"./fetch-wh-SFiy3.js";import{d as n,i as r,m as i,n as a,p as o,s}from"./catalogs-Dmr8bICW.js";import{n as c,t as l}from"./Card-DG7SRpy0.js";function u(e){return t({"/api/v1/metamodel/metatypes":{body:r},"/api/v1/catalogs":e})}var d,f,p,m,h,g,_,v;function y(){return(y=e((()=>{c(),i(),n(),{expect:d,userEvent:f,within:p}=__STORYBOOK_MODULE_TEST__,m={title:`Screens/SCR-028 Create Catalog`,component:o,render:e=>({components:{CreateCatalogForm:o,UCard:l},setup:()=>({args:e}),template:`
      <div class="flex min-h-screen items-start justify-center bg-elevated/50 p-8">
        <UCard class="w-full max-w-lg" :ui="{ header: 'flex flex-col gap-1' }">
          <template #header>
            <h2 class="text-lg font-semibold text-highlighted">New catalog</h2>
            <p class="text-sm text-muted">Create a table view of the repository.</p>
          </template>
          <CreateCatalogForm v-bind="args" />
        </UCard>
      </div>`})},h={args:{canManage:!0,fetchFn:u({status:201,body:a})}},g={args:{canManage:!1,fetchFn:u({status:201,body:a})}},_={args:{canManage:!0,fetchFn:u(s)},play:async({canvasElement:e})=>{let t=p(e);await f.type(await t.findByLabelText(/Name/),`Application Inventory`),await f.click(t.getByRole(`button`,{name:`Create catalog`})),await d(await t.findByText(`A catalog with this name already exists`)).toBeInTheDocument()}},v=[`Default`,`Viewer`,`NameCollision`]})))()}y();export{h as Default,_ as NameCollision,g as Viewer,v as __namedExportsOrder,m as default};